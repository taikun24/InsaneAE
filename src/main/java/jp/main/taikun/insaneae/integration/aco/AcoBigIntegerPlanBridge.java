package jp.main.taikun.insaneae.integration.aco;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.me.service.CraftingService;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * ACOの正確なBigInteger計画をInsaneAEのQuantum CPUへ渡す任意連携。
 *
 * <p>ACOの実装クラスを直接参照せず、公開APIの計画Viewだけを反射で読む。
 * ACOが無い・古い・設定で無効な場合は空を返し、標準AE2の計画を推測で
 * BigInteger扱いしない。</p>
 */
public final class AcoBigIntegerPlanBridge {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ACO_API_CLASS =
            "com.syaru.ae2craftingoptimizer.api.big.BigCraftingEngineApi";
    private static final AtomicReference<Methods> METHODS = new AtomicReference<>();
    private static final Methods UNAVAILABLE = new Methods(null, null, null, null);
    private static volatile boolean consumerRegistrationAttempted;

    private AcoBigIntegerPlanBridge() {
    }

    /** ACOの公開提出境界へ、既存BigIntegerクラフトCPUの受け入れ能力だけを登録する。 */
    public static synchronized void registerExternalPlanConsumer() {
        // 起動中に何度もAPI反射を行わず、Mod構築時の一回だけ登録する。
        if (consumerRegistrationAttempted) {
            return;
        }
        consumerRegistrationAttempted = true;
        try {
            Class<?> api = Class.forName(ACO_API_CLASS, false,
                    AcoBigIntegerPlanBridge.class.getClassLoader());
            int apiVersion = api.getField("EXTERNAL_CONSUMER_API_VERSION").getInt(null);
            // API世代が古い場合は標準CPUの安全な拒否境界を維持する。
            if (apiVersion < 1) {
                return;
            }
            api.getMethod("registerExternalBigIntegerPlanConsumer").invoke(null);
            LOGGER.info("InsaneAE: registered its BigInteger crafting CPU as an ACO plan consumer");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            LOGGER.debug("InsaneAE: ACO external BigInteger plan-consumer API is unavailable", failure);
        }
    }

    /** ACOが作ったBigInteger計画なら、正確なPattern回数を返す。 */
    public static Optional<Plan> inspect(ICraftingPlan plan) {
        if (plan == null) {
            return Optional.empty();
        }
        Methods methods = methods();
        if (methods.inspectPlan() == null) {
            return Optional.empty();
        }
        try {
            Object optional = methods.inspectPlan().invoke(null, plan);
            if (!(optional instanceof Optional<?> result) || result.isEmpty()) {
                return Optional.empty();
            }
            Object view = result.get();
            BigInteger exactBytes = (BigInteger) methods.exactBytes().invoke(view);
            @SuppressWarnings("unchecked")
            Map<IPatternDetails, BigInteger> patternTimes =
                    (Map<IPatternDetails, BigInteger>) methods.patternTimes().invoke(view);
            boolean simulation = (boolean) methods.simulation().invoke(view);
            return Optional.of(new Plan(exactBytes, simulation, Map.copyOf(patternTimes)));
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException failure) {
            return Optional.empty();
        }
    }

    /**
     * このフォークのQuantum CPUで、全Patternを同じ物理一括経路へ渡せるか。
     * 混在ツリーは推測で処理せず、標準AE2の明示的な未対応結果へ戻す。
     */
    public static boolean supportsQuantumCpu(Plan plan) {
        if (plan == null || plan.simulation() || plan.patternTimes().isEmpty()) {
            return false;
        }
        for (Map.Entry<IPatternDetails, BigInteger> entry : plan.patternTimes().entrySet()) {
            BigInteger amount = entry.getValue();
            // 0以下の回数は破損したPlanとして扱い、空のJobを作らない。
            if (entry.getKey() == null || amount == null || amount.signum() <= 0
                    || !(entry.getKey() instanceof IMolecularAssemblerSupportedPattern)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 実際にこのGridへQuantum CPUプロバイダが存在することまで確認する。
     * 存在しない通常CPUへExact Jobを受け付けると、飽和long taskが永久待機するため、
     * Patternの型だけでは受理しない。
     */
    public static boolean supportsQuantumCpu(Plan plan, IGrid grid) {
        if (!supportsQuantumCpu(plan) || grid == null
                || !(grid.getCraftingService() instanceof CraftingService service)) {
            return false;
        }
        for (IPatternDetails details : plan.patternTimes().keySet()) {
            boolean hasBulkProvider = false;
            for (ICraftingProvider provider : service.getProviders(details)) {
                if (provider instanceof jp.main.taikun.insaneae.quantum.IBulkCraftingProvider) {
                    hasBulkProvider = true;
                    break;
                }
            }
            if (!hasBulkProvider) {
                return false;
            }
        }
        return true;
    }

    private static Methods methods() {
        Methods current = METHODS.get();
        if (current != null) {
            return current == UNAVAILABLE ? UNAVAILABLE : current;
        }
        Methods resolved;
        try {
            Class<?> api = Class.forName(ACO_API_CLASS, false,
                    AcoBigIntegerPlanBridge.class.getClassLoader());
            int apiVersion = api.getField("CALCULATION_PROFILE_API_VERSION").getInt(null);
            if (apiVersion < 1) {
                throw new NoSuchMethodException("ACO calculation API is too old");
            }
            resolved = new Methods(
                    api.getMethod("inspectBigIntegerPlan", ICraftingPlan.class),
                    null,
                    null,
                    null);
            Class<?> viewType = Class.forName(
                    "com.syaru.ae2craftingoptimizer.api.big.BigIntegerCraftingPlanView",
                    false,
                    AcoBigIntegerPlanBridge.class.getClassLoader());
            resolved = new Methods(
                    resolved.inspectPlan(),
                    viewType.getMethod("exactBytes"),
                    viewType.getMethod("patternTimes"),
                    viewType.getMethod("simulation"));
        } catch (ReflectiveOperationException | LinkageError failure) {
            resolved = UNAVAILABLE;
        }
        METHODS.compareAndSet(null, resolved);
        return resolved;
    }

    /** Publicly immutable subset used by InsaneAE's job bridge. */
    public record Plan(
            BigInteger exactBytes,
            boolean simulation,
            Map<IPatternDetails, BigInteger> patternTimes) {
        public Plan {
            if (exactBytes == null || exactBytes.signum() < 0) {
                throw new IllegalArgumentException("exactBytes must not be negative");
            }
            patternTimes = Map.copyOf(patternTimes);
        }
    }

    private record Methods(
            Method inspectPlan,
            Method exactBytes,
            Method patternTimes,
            Method simulation) {
    }
}
