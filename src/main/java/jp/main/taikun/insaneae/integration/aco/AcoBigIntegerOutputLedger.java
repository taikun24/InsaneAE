package jp.main.taikun.insaneae.integration.aco;

import appeng.api.stacks.AEKey;
import com.mojang.logging.LogUtils;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.Map;
import java.util.Optional;
import jp.main.taikun.insaneae.util.LogOnce;
import org.slf4j.Logger;

/**
 * ACO (AE2 Crafting Optimizer) の公開 BigInteger 台帳 API を呼ぶ任意連携アダプター。
 *
 * <p>ACO は任意依存なので、このクラスだけが外部 API を名前で解決する。InsaneAE 本体の
 * Quantum CPU 処理は ACO のクラスを直接参照せず、古い ACO や ACO なしでもロードできる。</p>
 *
 * <p>注意点 2 つ (ACO 1.5.11 の jar を javap で確認済み):</p>
 * <ul>
 *   <li>{@code BigIntegerAmountLedger<K>} はジェネリックなので、消去後のシグネチャは
 *       {@code add(Object, BigInteger)} / {@code drain(Object, long)}。
 *       引数の型で厳密に引かず、{@link #findKeyAmountMethod} で「AEKey を渡せる」ものを探す。</li>
 *   <li>{@code createAmountLedger} の引数型 ({@code engine.BigCraftingKeyCodec}) は
 *       ACO の内部パッケージにある。型を {@code Class.forName} で名指ししないよう、
 *       内部型を要求しない {@code createAeKeyAmountLedger} を使う。</li>
 * </ul>
 *
 * <h2>解決は 1 回だけ</h2>
 * <p>台帳は Quantum CPU 1 台につき 1 つ作るが、API の解決 (クラスとメソッドの検索・
 * 版の確認) とログは<b>プロセスで 1 回だけ</b>にしている。以前は台帳を作るたびに
 * 解決とログの出力をしており、Quantum CPU の多いワールドではブロックエンティティが
 * 作られるたびに INFO が 1 行出てコンソールが埋まった (issue #18)。</p>
 *
 * <p>実行時に ACO 側が例外を投げた場合の面倒 (ローカル台帳への退避) は
 * {@link LazyFailsafeLedger} が見る。ここでは {@link IllegalStateException} に包んで投げ上げるだけ。</p>
 */
final class AcoBigIntegerOutputLedger implements PendingOutputLedger {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String LEDGER_CLASS =
            "com.syaru.ae2craftingoptimizer.api.big.BigIntegerAmountLedger";
    /** ACO 1.5.12で追加された、内部codec型を要求しないAEKey台帳API。 */
    private static final int REQUIRED_LEDGER_API_VERSION = 2;
    private static final int REQUIRED_API_VERSION = 3;

    /** 解決済みの API。空なら ACO の台帳は使えない (古い・見つからない)。 */
    private static volatile Optional<Binding> binding;

    /** ACO の台帳 API のメソッド一式。 */
    private record Binding(
            Method isEnabled,
            Method create,
            Method add,
            Method drain,
            Method snapshot,
            Method isEmpty,
            Method clear) {
    }

    private final Object delegate;
    private final Binding api;

    private AcoBigIntegerOutputLedger(Object delegate, Binding api) {
        this.delegate = delegate;
        this.api = api;
    }

    /** ACO APIが無い、古い、または無効設定ならemptyを返す。 */
    static Optional<PendingOutputLedger> tryCreate() {
        Binding api = binding().orElse(null);
        if (api == null) {
            return Optional.empty();
        }
        try {
            // 有効・無効は ACO の設定で変わり得るので、解決とは別に毎回聞く。
            if (!(Boolean) api.isEnabled().invoke(null)) {
                if (LogOnce.first("aco-ledger-disabled")) {
                    LOGGER.info("InsaneAE: ACO BigInteger backend is disabled; using local ledger");
                }
                return Optional.empty();
            }
            Object ledger = api.create().invoke(null);
            if (LogOnce.first("aco-ledger-enabled")) {
                LOGGER.info("InsaneAE: ACO BigInteger amount ledger integration enabled");
            }
            return Optional.of(new AcoBigIntegerOutputLedger(ledger, api));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            if (LogOnce.first("aco-ledger-create-failed")) {
                LOGGER.warn("InsaneAE: could not create an ACO BigInteger ledger; using local ledger", failure);
            }
            return Optional.empty();
        }
    }

    private static Optional<Binding> binding() {
        Optional<Binding> resolved = binding;
        if (resolved == null) {
            synchronized (AcoBigIntegerOutputLedger.class) {
                resolved = binding;
                if (resolved == null) {
                    resolved = resolve();
                    binding = resolved;
                }
            }
        }
        return resolved;
    }

    private static Optional<Binding> resolve() {
        try {
            Class<?> api = Class.forName(AcoClassNames.BIG_CRAFTING_ENGINE_API);
            int apiVersion = api.getField("API_VERSION").getInt(null);
            int ledgerVersion = api.getField("AMOUNT_LEDGER_API_VERSION").getInt(null);
            if (apiVersion < REQUIRED_API_VERSION || ledgerVersion < REQUIRED_LEDGER_API_VERSION) {
                LOGGER.warn(
                        "InsaneAE: ACO BigInteger API v{} / amount ledger v{} is too old; using local ledger",
                        apiVersion,
                        ledgerVersion);
                return Optional.empty();
            }
            Class<?> type = Class.forName(LEDGER_CLASS);
            return Optional.of(new Binding(
                    api.getMethod("isEnabled"),
                    api.getMethod("createAeKeyAmountLedger"),
                    findKeyAmountMethod(type, "add", BigInteger.class),
                    findKeyAmountMethod(type, "drain", long.class),
                    type.getMethod("snapshot"),
                    type.getMethod("isEmpty"),
                    type.getMethod("clear")));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            LOGGER.warn("InsaneAE: ACO BigInteger API is unavailable; using local ledger", failure);
            return Optional.empty();
        }
    }

    /**
     * ジェネリックなKは実行時にObjectへ型消去されるため、AEKey固定の厳密検索を避ける。
     * 第1引数へAEKeyを渡せて、第2引数の量型が一致する公開メソッドだけを採用する
     * (BigInteger の加算と long の取り出しを取り違えないよう、量の型は一致させる)。
     */
    private static Method findKeyAmountMethod(Class<?> type, String name, Class<?> amountType)
            throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (method.getName().equals(name)
                    && parameterTypes.length == 2
                    && parameterTypes[0].isAssignableFrom(AEKey.class)
                    && parameterTypes[1].equals(amountType)) {
                return method;
            }
        }
        throw new NoSuchMethodException(
                type.getName() + "." + name + "(AEKey," + amountType.getTypeName() + ")");
    }

    @Override
    public void add(AEKey key, BigInteger amount) {
        if (key == null || amount == null || amount.signum() <= 0) {
            return;
        }
        invoke(api.add(), key, amount);
    }

    @Override
    public long drain(AEKey key, long maximum) {
        if (key == null || maximum <= 0L) {
            return 0L;
        }
        return (Long) invoke(api.drain(), key, maximum);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<AEKey, BigInteger> snapshot() {
        return (Map<AEKey, BigInteger>) invoke(api.snapshot());
    }

    @Override
    public boolean isEmpty() {
        return (Boolean) invoke(api.isEmpty());
    }

    @Override
    public void clear() {
        invoke(api.clear());
    }

    private Object invoke(Method method, Object... arguments) {
        try {
            return method.invoke(delegate, arguments);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("ACO BigInteger API method is inaccessible: " + method, exception);
        } catch (InvocationTargetException exception) {
            throw new IllegalStateException("ACO BigInteger API method failed: " + method,
                    exception.getCause());
        }
    }
}
