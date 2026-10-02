package jp.main.taikun.insaneae.integration.extendedae;

import jp.main.taikun.insaneae.integration.AddonIntegration;
import jp.main.taikun.insaneae.registries.ModUpgrades;

import java.util.List;

/**
 * ExtendedAE の機械にこちらの加速カードを挿せるようにする。
 *
 * <p>ExtendedAE Plus の機械は速度カード対象のものが無い (独自のエンティティ加速カード系のみ)。
 * 効かせる側は mixin/compat の {@code Ex〜Mixin} (@Pseudo)。ここは「挿せる」ようにするだけで、
 * ExtendedAE のクラスは参照しない (登録名からアイテムを引く)。</p>
 */
public final class ExtendedAeIntegration implements AddonIntegration {

    /** 1.20.1 の modid。1.21 では {@code extendedae} に変わった。 */
    public static final String MODID = "expatternprovider";

    @Override
    public String modId() {
        return MODID;
    }

    @Override
    public void registerUpgrades() {
        ModUpgrades.allowSpeedCards(MODID, List.of(
                "ex_import_bus_part",       // 基底 IOBusPart の Mixin が効く
                "ex_export_bus_part",
                "tag_export_bus",
                "mod_export_bus",
                "precise_export_bus",
                "threshold_export_bus",
                "active_formation_plane",   // ExFormationPlaneMixin
                "ex_molecular_assembler",   // ExCraftingThreadMixin
                "ex_inscriber",             // ExInscriberThreadMixin
                "ex_io_port",               // ExIOPortMixin
                "circuit_cutter"));         // ExCircuitCutterMixin
    }
}
