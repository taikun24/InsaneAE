package jp.main.taikun.insaneae.integration.aco;

import appeng.api.stacks.AEKey;
import com.mojang.logging.LogUtils;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.Map;
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
 *       {@code getMethod} には {@code AEKey.class} ではなく <b>{@code Object.class}</b> を渡すこと。</li>
 *   <li>{@code createAmountLedger} の引数型 ({@code engine.BigCraftingKeyCodec}) は
 *       ACO の内部パッケージにある。型を {@code Class.forName} で名指ししないよう、
 *       メソッドは<b>名前で探す</b>。</li>
 * </ul>
 *
 * <p>実行時に ACO 側が例外を投げた場合の面倒 (ローカル台帳への退避) は
 * {@link OptionalAcoBigIntegerIntegration} のフェイルセーフが見る。ここでは
 * {@link IllegalStateException} に包んで投げ上げるだけ。</p>
 */
final class AcoBigIntegerOutputLedger implements PendingOutputLedger {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String API_CLASS =
            "com.syaru.ae2craftingoptimizer.api.big.BigCraftingEngineApi";
    private static final String LEDGER_CLASS =
            "com.syaru.ae2craftingoptimizer.api.big.BigIntegerAmountLedger";
    /** ACO 1.5.12で追加された、内部codec型を要求しないAEKey台帳API。 */
    private static final int REQUIRED_LEDGER_API_VERSION = 2;

    private final Object delegate;
    private final Method add;
    private final Method drain;
    private final Method snapshot;
    private final Method isEmpty;
    private final Method clear;

    private AcoBigIntegerOutputLedger(
            Object delegate,
            Method add,
            Method drain,
            Method snapshot,
            Method isEmpty,
            Method clear) {
        this.delegate = delegate;
        this.add = add;
        this.drain = drain;
        this.snapshot = snapshot;
        this.isEmpty = isEmpty;
        this.clear = clear;
    }

    /** ACO APIが無い、古い、または無効設定ならemptyを返す。 */
    static java.util.Optional<PendingOutputLedger> tryCreate() {
        try {
            Class<?> api = Class.forName(API_CLASS);
            int apiVersion = api.getField("API_VERSION").getInt(null);
            int ledgerVersion = api.getField("AMOUNT_LEDGER_API_VERSION").getInt(null);
            if (apiVersion < 3 || ledgerVersion < REQUIRED_LEDGER_API_VERSION) {
                LOGGER.warn(
                        "InsaneAE: ACO BigInteger API v{} / amount ledger v{} is too old; using local ledger",
                        apiVersion,
                        ledgerVersion);
                return java.util.Optional.empty();
            }
            if (!(Boolean) api.getMethod("isEnabled").invoke(null)) {
                LOGGER.info("InsaneAE: ACO BigInteger backend is disabled; using local ledger");
                return java.util.Optional.empty();
            }

            Object ledger = api.getMethod("createAeKeyAmountLedger").invoke(null);
            Class<?> type = Class.forName(LEDGER_CLASS);
            return java.util.Optional.of(new AcoBigIntegerOutputLedger(
                    ledger,
                    findKeyAmountMethod(type, "add", BigInteger.class),
                    findKeyAmountMethod(type, "drain", long.class),
                    type.getMethod("snapshot"),
                    type.getMethod("isEmpty"),
                    type.getMethod("clear")));
        } catch (Throwable failure) {
            LOGGER.warn("InsaneAE: ACO BigInteger API is unavailable; using local ledger", failure);
            return java.util.Optional.empty();
        }
    }

    /**
     * ジェネリックなKは実行時にObjectへ型消去されるため、AEKey固定の厳密検索を避ける。
     * 第1引数へAEKeyを渡せて、第2引数の量型が一致する公開メソッドだけを採用する。
     */
    private static Method findKeyAmountMethod(Class<?> type, String name, Class<?> amountType)
            throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            // 対象メソッド名以外は候補から除外する。
            if (!method.getName().equals(name)) {
                continue;
            }
            // キーと量の2引数でないメソッドは対象外にする。
            if (parameterTypes.length != 2) {
                continue;
            }
            // 型消去後のObject、またはAEKey互換型だけを受け入れる。
            if (!parameterTypes[0].isAssignableFrom(AEKey.class)) {
                continue;
            }
            // BigInteger加算とlong搬出を取り違えないよう量型を一致させる。
            if (!parameterTypes[1].equals(amountType)) {
                continue;
            }
            return method;
        }
        throw new NoSuchMethodException(
                type.getName() + "." + name + "(AEKey," + amountType.getTypeName() + ")");
    }

    @Override
    public void add(AEKey key, BigInteger amount) {
        if (key == null || amount == null || amount.signum() <= 0) {
            return;
        }
        invoke(add, key, amount);
    }

    @Override
    public long drain(AEKey key, long maximum) {
        if (key == null || maximum <= 0L) {
            return 0L;
        }
        return (Long) invoke(drain, key, maximum);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<AEKey, BigInteger> snapshot() {
        return (Map<AEKey, BigInteger>) invoke(snapshot);
    }

    @Override
    public boolean isEmpty() {
        return (Boolean) invoke(isEmpty);
    }

    @Override
    public void clear() {
        invoke(clear);
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
