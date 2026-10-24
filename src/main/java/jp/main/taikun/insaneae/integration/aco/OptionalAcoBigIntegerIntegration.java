package jp.main.taikun.insaneae.integration.aco;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

/** ACOが導入・有効な場合だけ公開BigInteger APIを選ぶ入口。 */
public final class OptionalAcoBigIntegerIntegration {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ACO_MOD_ID = "ae2_crafting_optimizer";

    private OptionalAcoBigIntegerIntegration() {
    }

    /**
     * <b>BigInteger 計画の外部コンシューマとして名乗る。</b>
     *
     * <p>ACO の設定 {@code enableExternalBigCraftingProfile} の説明どおり
     * 「アドオン側が公開 API で明示的に登録する」必要がある。名乗っていないと
     * {@code CraftingCpuClusterBigCapacityGuardMixin} が wide plan の投入を蹴るため、
     * 標準の AE2 クラフト CPU 経由で 922京超の注文を出したときに<b>理由の分からない失敗</b>になる。</p>
     *
     * <p>反射で呼ぶのは、ACO が無い環境でこのクラスが読めなくならないようにするため
     * (このクラスは Quantum CPU の生成時に必ず通る)。呼べなければ黙って何もしない。</p>
     */
    public static void registerBigIntegerPlanConsumer() {
        if (!ModList.get().isLoaded(ACO_MOD_ID)) {
            return;
        }
        try {
            Class<?> api = Class.forName(AcoClassNames.BIG_CRAFTING_ENGINE_API,
                    false, OptionalAcoBigIntegerIntegration.class.getClassLoader());
            api.getMethod("registerExternalBigIntegerPlanConsumer").invoke(null);
            LOGGER.info("InsaneAE: registered as an ACO external BigInteger plan consumer");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) {
            LOGGER.debug("InsaneAE: ACO external BigInteger plan consumer API is unavailable", unavailable);
        }
    }

    /**
     * Quantum CPU 1 台ぶんの完成品待ちの台帳を作る。
     *
     * <p>実体 (ACO の台帳か内蔵の台帳か) は、<b>最初に何かを積むときに</b>選ぶ
     * ({@link LazyFailsafeLedger})。ブロックエンティティを作るだけでは ACO に触らず、
     * ログも出さない (issue #18: 作られるたびに INFO が出てコンソールが埋まっていた)。</p>
     */
    public static PendingOutputLedger createOutputLedger() {
        return new LazyFailsafeLedger(OptionalAcoBigIntegerIntegration::openBackingLedger);
    }

    private static PendingOutputLedger openBackingLedger() {
        // ACOが無い場合は同じ正確会計の内蔵実装へ戻し、通常のInsaneAE導入を壊さない。
        if (ModList.get().isLoaded(ACO_MOD_ID)) {
            PendingOutputLedger aco = AcoBigIntegerOutputLedger.tryCreate().orElse(null);
            if (aco != null) {
                return aco;
            }
        }
        return new LocalBigIntegerOutputLedger();
    }
}
