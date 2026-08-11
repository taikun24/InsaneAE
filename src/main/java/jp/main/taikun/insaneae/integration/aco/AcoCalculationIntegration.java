package jp.main.taikun.insaneae.integration.aco;

import com.mojang.logging.LogUtils;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

/**
 * ACOの計算境界を任意依存のまま確認する入口。
 *
 * <p>InsaneAEはACOへコンパイル依存しない。ACOが無い場合やAPIが古い場合は
 * falseを返し、従来のInsaneAE計算バッチへ戻る。クラス参照はこのクラス内の
 * 反射に閉じ込め、Dedicated Serverでクライアント専用型を読み込まない。</p>
 */
public final class AcoCalculationIntegration {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ACO_MOD_ID = "ae2_crafting_optimizer";
    private static final String ACO_API_CLASS =
            "com.syaru.ae2craftingoptimizer.api.big.BigCraftingEngineApi";
    private static final int MINIMUM_CALCULATION_PROFILE_API_VERSION = 1;

    private static volatile Method calculationProfileMethod;
    private static volatile boolean lookupComplete;

    private AcoCalculationIntegration() {
    }

    /**
     * ACOが厳密計算を所有している間、InsaneAEの計算用バッチを重ねない。
     * 実クラフトの一括処理はこの判定の対象外で、Quantum CPUの高速経路は維持する。
     */
    public static boolean shouldDeferCalculationBatch() {
        if (!ModList.get().isLoaded(ACO_MOD_ID)) {
            return false;
        }
        Method method = findCalculationProfileMethod();
        if (method == null) {
            return false;
        }
        try {
            return (boolean) method.invoke(null);
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException failure) {
            LOGGER.debug("InsaneAE: ACO calculation capability query failed; keeping native batching", failure);
            return false;
        }
    }

    private static Method findCalculationProfileMethod() {
        if (lookupComplete) {
            return calculationProfileMethod;
        }
        synchronized (AcoCalculationIntegration.class) {
            if (lookupComplete) {
                return calculationProfileMethod;
            }
            try {
                Class<?> api = Class.forName(ACO_API_CLASS, false,
                        AcoCalculationIntegration.class.getClassLoader());
                int apiVersion = api.getField("CALCULATION_PROFILE_API_VERSION").getInt(null);
                if (apiVersion < MINIMUM_CALCULATION_PROFILE_API_VERSION) {
                    LOGGER.info("InsaneAE: ACO calculation API v{} is too old; keeping native batching",
                            apiVersion);
                } else {
                    calculationProfileMethod = api.getMethod("isCalculationProfileActive");
                }
            } catch (ClassNotFoundException | NoSuchFieldException | NoSuchMethodException
                    | IllegalAccessException | LinkageError failure) {
                LOGGER.debug("InsaneAE: ACO calculation API is unavailable; keeping native batching");
            }
            lookupComplete = true;
            return calculationProfileMethod;
        }
    }
}
