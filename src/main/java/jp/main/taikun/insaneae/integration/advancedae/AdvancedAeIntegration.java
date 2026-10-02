package jp.main.taikun.insaneae.integration.advancedae;

import jp.main.taikun.insaneae.integration.AddonIntegration;
import jp.main.taikun.insaneae.registries.ModUpgrades;

import java.util.List;

/**
 * Advanced AE の機械にこちらの加速カードを挿せるようにする。
 *
 * <p>効かせる側は mixin/compat の {@code AAE〜Mixin} (@Pseudo)。バス 3 種は
 * AE2 の ExportBusPart/IOBusPart 経由で効くので登録だけでよい。</p>
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
