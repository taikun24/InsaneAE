package jp.main.taikun.insaneae.integration.advancedae;

import jp.main.taikun.insaneae.integration.AddonIntegration;
import jp.main.taikun.insaneae.registries.ModUpgrades;

import java.util.List;

/**
 * Advanced AEの機械にこちらの加速カードを挿せるようにする。
 *
 * <p>機械の処理速度に反映させる側はmixin/compatの {@code AAE〜Mixin} (@Pseudo)。バス3種は
 * AE2のExportBusPart/IOBusPart経由で反映されるので登録だけでよい。</p>
 */
public final class AdvancedAeIntegration implements AddonIntegration {

    public static final String MODID = "advanced_ae";

    @Override
    public String modId() {
        return MODID;
    }

    @Override
    public void registerUpgrades() {
        ModUpgrades.allowSpeedCards(MODID, List.of(
                "stock_export_bus_part",
                "import_export_bus_part",
                "advanced_io_bus_part",
                "quantum_crafter",          // AAEQuantumCrafterMixin
                "reaction_chamber"));       // AAEReactionChamberMixin
    }
}
