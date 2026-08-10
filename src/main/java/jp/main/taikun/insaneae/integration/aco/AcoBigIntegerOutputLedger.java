package jp.main.taikun.insaneae.integration.aco;

import appeng.api.stacks.AEKey;
import com.mojang.logging.LogUtils;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.Map;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import org.slf4j.Logger;

/**
 * ACO 4以上の公開APIだけを呼ぶ任意連携アダプター。
 *
 * <p>ACOは任意依存なので、このクラスだけが外部APIを名前で解決する。InsaneAE本体の
 * Quantum CPU処理はACOのクラスを直接参照せず、古いACOやACOなしでもロードできる。</p>
 */
final class AcoBigIntegerOutputLedger implements PendingOutputLedger {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String API_CLASS =
            "com.syaru.ae2craftingoptimizer.api.big.BigCraftingEngineApi";
    private static final String CODEC_CLASS =
            "com.syaru.ae2craftingoptimizer.api.big.AeKeyBigCraftingCodec";
    private static final String LEDGER_CLASS =
            "com.syaru.ae2craftingoptimizer.api.big.BigIntegerAmountLedger";

    private final Object delegate;
    private final Method add;
    private final Method drain;
    private final Method snapshot;
    private final Method isEmpty;
    private final Method save;
    private final Method load;
    private final Method clear;

    private AcoBigIntegerOutputLedger(
            Object delegate,
            Method add,
            Method drain,
            Method snapshot,
            Method isEmpty,
            Method save,
            Method load,
            Method clear) {
        this.delegate = delegate;
        this.add = add;
        this.drain = drain;
        this.snapshot = snapshot;
        this.isEmpty = isEmpty;
        this.save = save;
        this.load = load;
        this.clear = clear;
    }

    /** ACO APIが無い、古い、または無効設定ならemptyを返す。 */
    static java.util.Optional<PendingOutputLedger> tryCreate() {
        try {
            Class<?> api = Class.forName(API_CLASS);
            int apiVersion = api.getField("API_VERSION").getInt(null);
            if (apiVersion < 4) {
                LOGGER.warn("InsaneAE: ACO BigInteger API version {} is too old; using local ledger", apiVersion);
                return java.util.Optional.empty();
            }
            if (!(Boolean) api.getMethod("isEnabled").invoke(null)) {
                LOGGER.info("InsaneAE: ACO BigInteger backend is disabled; using local ledger");
                return java.util.Optional.empty();
            }

            Class<?> codecType = Class.forName(
                    "com.syaru.ae2craftingoptimizer.engine.BigCraftingKeyCodec");
            Object codec = Class.forName(CODEC_CLASS).getField("INSTANCE").get(null);
            Object ledger = api.getMethod("createAmountLedger", codecType).invoke(null, codec);
            Class<?> type = Class.forName(LEDGER_CLASS);
            return java.util.Optional.of(new AcoBigIntegerOutputLedger(
                    ledger,
                    type.getMethod("add", AEKey.class, BigInteger.class),
                    type.getMethod("drain", AEKey.class, long.class),
                    type.getMethod("snapshot"),
                    type.getMethod("isEmpty"),
                    type.getMethod("save"),
                    type.getMethod("load", CompoundTag.class),
                    type.getMethod("clear")));
        } catch (Throwable failure) {
            LOGGER.warn("InsaneAE: ACO BigInteger API is unavailable; using local ledger", failure);
            return java.util.Optional.empty();
        }
    }

    @Override
    public void add(AEKey key, BigInteger amount) {
        invoke(add, key, amount);
    }

    @Override
    public long drain(AEKey key, long maximum) {
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
    public CompoundTag save() {
        return (CompoundTag) invoke(save);
    }

    @Override
    public void load(CompoundTag saved) {
        invoke(load, Objects.requireNonNull(saved, "saved"));
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
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("ACO BigInteger API method failed: " + method, cause);
        }
    }
}
