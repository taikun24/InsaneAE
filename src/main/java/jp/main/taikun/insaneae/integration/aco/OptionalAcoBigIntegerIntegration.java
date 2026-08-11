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

    public static PendingOutputLedger createOutputLedger() {
        // ACOが無い場合は同じ正確会計の内蔵実装へ戻し、通常のInsaneAE導入を壊さない。
        if (!ModList.get().isLoaded(ACO_MOD_ID)) {
            return new LocalBigIntegerOutputLedger();
        }
        PendingOutputLedger ledger = AcoBigIntegerOutputLedger.tryCreate().orElse(null);
        if (ledger != null) {
            LOGGER.info("InsaneAE: ACO BigInteger amount ledger integration enabled");
            return ledger;
        }
        return new LocalBigIntegerOutputLedger();
    }
}
