package jp.main.taikun.insaneae.datagen;

import jp.main.taikun.insaneae.InsaneAE;
import jp.main.taikun.insaneae.crafting.InsaneAcceleratorType;
import jp.main.taikun.insaneae.energy.InsaneEnergyCellTier;
import jp.main.taikun.insaneae.energy.SolarPanelTier;
import jp.main.taikun.insaneae.upgrade.InsaneSpeedCardType;
import jp.main.taikun.insaneae.crafting.InsaneCraftingUnitType;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.model.generators.ItemModelBuilder;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.common.data.ExistingFileHelper;

/**
 * アイテムモデル。
 *
 * <p>セル系は AE2 / MEGA Cells と同じ見た目の続きにする。<b>階層で色が変わる部分だけ</b>
 * {@code tools/gen_cell_textures.py} が階層色から生成し (セルコンポーネント / 通常セルの窓と帯 /
 * ポータブルセルの側面)、階層に依らないハウジング・LED・画面は MEGA / AE2 のものを
 * レイヤで重ねる。生成側のマスクは借りているハウジングのドット位置に合わせてあるので、
 * ハウジングを差し替えるならマスクも描き直すこと。</p>
 *
 * <p>レイヤ番号には意味がある。AE2 の色ハンドラ ({@code BasicStorageCell#getColor} /
 * {@code AbstractPortableCell#getColor}) が <b>layer1 を中身の量の色</b>、
 * ポータブルはさらに <b>layer2 を染色色</b> として塗るので、この順番は動かせない。</p>
 *
 * <p>化学物質セルは Applied Mekanistics 未導入だとアイテム自体が登録されないが、
 * モデルは名前だけで生成できるので常に出力しておく (使われないだけで害はない)。</p>
 */
public class ModItemModelProvider extends ItemModelProvider {

    private static final ModelFile GENERATED = new ModelFile.UncheckedModelFile("item/generated");

    private static final ResourceLocation CELL_LED = mega("ae2", "item/storage_cell_led");
    private static final ResourceLocation PORTABLE_LED = mega("ae2", "item/portable_cell_led");
    /** 通常セルのハウジング。MEGA の 1M〜256M と同じ見た目の続きにする。 */
    private static final ResourceLocation ITEM_HOUSING = mega("megacells", "item/mega_item_cell_housing");
    private static final ResourceLocation FLUID_HOUSING = mega("megacells", "item/mega_fluid_cell_housing");
    private static final ResourceLocation CHEMICAL_HOUSING = mega("megacells", "item/mega_chemical_cell_housing");
    private static final ResourceLocation PORTABLE_ITEM_SCREEN =
            mega("megacells", "item/cell/portable/portable_cell_item_screen");
    private static final ResourceLocation PORTABLE_FLUID_SCREEN =
            mega("megacells", "item/cell/portable/portable_cell_fluid_screen");
    private static final ResourceLocation PORTABLE_ITEM_HOUSING = mega("ae2", "item/portable_cell_item_housing");
    private static final ResourceLocation PORTABLE_FLUID_HOUSING = mega("ae2", "item/portable_cell_fluid_housing");
    /** 化学物質のポータブルセル用。AE2 に専用の筐体が無いので汎用のものを使う。 */
    private static final ResourceLocation PORTABLE_HOUSING = mega("ae2", "item/portable_cell_housing");
    private static final ResourceLocation SPEED_CARD = mega("ae2", "item/card_speed");
    /** AE2 の {@code InitItemModelsProperties} が登録するエネルギーセルの残量プロパティ。 */
    private static final ResourceLocation ENERGY_FILL_LEVEL = mega("ae2", "fill_level");

    private static ResourceLocation mega(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    public ModItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, InsaneAE.MODID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        for (InsaneCraftingUnitType tier : InsaneCraftingUnitType.values()) {
            String id = tier.id();

            // クラフトストレージのアイテムは未 formed のブロックモデルをそのまま使う。
            getBuilder(tier.blockId()).parent(new ModelFile.UncheckedModelFile(
                    ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID, "block/crafting/" + id + "_storage")));

            layered(tier.cellComponentId(), component(id));

            // ハウジング → LED (layer1 が中身の量で着色される) → 階層色、の順で重ねる。
            layered("item_storage_cell_" + id, ITEM_HOUSING, CELL_LED, standardCell(id));
            layered("fluid_storage_cell_" + id, FLUID_HOUSING, CELL_LED, standardCell(id));
            layered("chemical_storage_cell_" + id, CHEMICAL_HOUSING, CELL_LED, standardCell(id));

            layered("portable_item_cell_" + id,
                    PORTABLE_ITEM_SCREEN, PORTABLE_LED, PORTABLE_ITEM_HOUSING, portableSide(id));
            layered("portable_fluid_cell_" + id,
                    PORTABLE_FLUID_SCREEN, PORTABLE_LED, PORTABLE_FLUID_HOUSING, portableSide(id));
            layered("portable_chemical_cell_" + id,
                    PORTABLE_ITEM_SCREEN, PORTABLE_LED, PORTABLE_HOUSING, portableSide(id));
        }

        simple("creative_cell");

        for (InsaneSpeedCardType card : InsaneSpeedCardType.values()) {
            simple(card.id());
        }
        simple("quantum_acceleration_card");
        simple("task_fusion_card");

        getBuilder("quantum_cpu").parent(new ModelFile.UncheckedModelFile(
                ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID, "block/quantum_cpu")));
        // BigInteger CPU も通常 Quantum CPU の完成済みモデルを直接参照する。
        getBuilder("big_integer_cpu").parent(new ModelFile.UncheckedModelFile(
                ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID, "block/quantum_cpu")));
        getBuilder("improved_charger").parent(new ModelFile.UncheckedModelFile(
                ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID, "block/improved_charger")));
        getBuilder("insane_interface").parent(new ModelFile.UncheckedModelFile(
                ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID, "block/insane_interface")));
        getBuilder("insane_pattern_provider").parent(new ModelFile.UncheckedModelFile(
                ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID, "block/insane_pattern_provider")));

        for (SolarPanelTier tier : SolarPanelTier.values()) {
            getBuilder(tier.id()).parent(new ModelFile.UncheckedModelFile(
                    ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID, "block/" + tier.id())));
        }

        for (InsaneEnergyCellTier tier : InsaneEnergyCellTier.values()) {
            energyCell(tier);
        }

        for (InsaneAcceleratorType tier : InsaneAcceleratorType.values()) {
            getBuilder(tier.blockId()).parent(new ModelFile.UncheckedModelFile(
                    ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID, "block/crafting/" + tier.id() + "_accelerator")));
        }
    }

    /**
     * エネルギーセルのアイテムモデル。
     *
     * <p>AE2 は {@code EnergyCellBlockItem} を registry から拾って
     * {@code ae2:fill_level} (現在値 / 最大値) を登録するので、
     * ブロックと同じ 5 段階のモデルを override で切り替えれば残量が見た目に出る。</p>
     */
    private void energyCell(InsaneEnergyCellTier tier) {
        ItemModelBuilder builder = getBuilder(tier.id()).parent(energyCellModel(tier, 0));
        for (int fullness = 1; fullness <= 4; fullness++) {
            builder.override()
                    .predicate(ENERGY_FILL_LEVEL, fullness * 0.2F)
                    .model(energyCellModel(tier, fullness))
                    .end();
        }
    }

    private ModelFile energyCellModel(InsaneEnergyCellTier tier, int fullness) {
        return new ModelFile.UncheckedModelFile(ResourceLocation.fromNamespaceAndPath(
                InsaneAE.MODID, ModBlockStateProvider.energyCellModelPath(tier, fullness)));
    }
    private void simple(String name){
        layered(name, mega("insaneae", "item/" + name));
    }

    /** {@code tools/gen_cell_textures.py} が出すセルコンポーネントのテクスチャ。 */
    private static ResourceLocation component(String tier) {
        return mega(InsaneAE.MODID, "item/cell_component_" + tier);
    }

    /** 同上、通常セルの階層色レイヤ (ハウジングの窓と左面の帯だけを描いたもの)。 */
    private static ResourceLocation standardCell(String tier) {
        return mega(InsaneAE.MODID, "item/cell/standard/storage_cell_" + tier);
    }

    /** 同上、ポータブルセルの側面 (階層色の帯だけを描いたレイヤ)。 */
    private static ResourceLocation portableSide(String tier) {
        return mega(InsaneAE.MODID, "item/cell/portable/portable_cell_side_" + tier);
    }

    /** {@code item/generated} + レイヤ指定のモデルを 1 件出す。 */
    private void layered(String name, ResourceLocation... layers) {
        ItemModelBuilder builder = getBuilder(name).parent(GENERATED);
        for (int layer = 0; layer < layers.length; layer++) {
            builder.texture("layer" + layer, layers[layer]);
        }
    }
}
