package jp.main.taikun.insaneae.datagen;

import jp.main.taikun.insaneae.InsaneAE;
import jp.main.taikun.insaneae.crafting.InsaneAcceleratorType;
import jp.main.taikun.insaneae.energy.InsaneEnergyCellTier;
import jp.main.taikun.insaneae.energy.SolarPanelTier;
import jp.main.taikun.insaneae.upgrade.InsaneSpeedCardType;
import jp.main.taikun.insaneae.crafting.InsaneCraftingUnitType;
import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.generators.ItemModelBuilder;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/**
 * アイテムモデル。
 *
 * <p>セル系は AE2 / MEGA Cells と同じ見た目の続きにする。<b>階層で色が変わる部分だけ</b>
 * {@code tools/gen_cell_textures.py} が階層色から生成し (セルコンポーネント / 通常セルの窓と帯 /
 * ポータブルセルの側面)、階層に依らないハウジング・LED・画面は MEGA / AE2 のものを
 * レイヤで重ねる。生成側のマスクは借りているハウジングのドット位置に合わせてあるので、
 * ハウジングを差し替えるならマスクも描き直すこと。</p>
 *
 * <p><b>レイヤ番号には意味がある。</b>{@code item/generated} は layerN に tintindex N を振り、
 * AE2 の色ハンドラ ({@code BasicStorageCell#getColor} / {@code AbstractPortableCell#getColor}) が
 * <b>tintindex 1 を中身の量の色</b>、ポータブルはさらに <b>tintindex 2 を画面の色</b>として塗る。
 * したがってレイヤの順番は AE2 / MEGA 本体のモデルと 1 ドットも違えてはならない。</p>
 *
 * <p>ポータブルセルの順番は <b>ハウジング → LED → 画面 → 側面</b>。
 * 1.20.1 では画面が layer0・ハウジングが layer2 だったが、AE2 19.2 で入れ替わった
 * ({@code ae2:item/portable_item_cell_1k} 参照)。<b>古い順番のままだと不透明なハウジングが
 * 一番上に来て他のレイヤを全部隠し、そのうえ染色色 (既定は白) で塗られるので、
 * ポータブルセルが真っ白な塊になる。</b></p>
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
    /**
     * ポータブルセルの画面部分。
     *
     * <p>MEGA Cells 4.x でアイテム用 / 液体用に分かれていた
     * {@code megacells:item/cell/portable/portable_cell_*_screen} が無くなり、
     * MEGA 自身のポータブルセルも AE2 の汎用テクスチャ 1 枚を使うようになったので、そちらに合わせる。</p>
     */
    private static final ResourceLocation PORTABLE_ITEM_SCREEN = mega("ae2", "item/portable_cell_screen");
    private static final ResourceLocation PORTABLE_FLUID_SCREEN = PORTABLE_ITEM_SCREEN;
    /**
     * ポータブルセルの筐体。<b>MEGA のものを使う</b> (AE2 のものではない)。
     *
     * <p>AE2 19.2 の {@code ae2:item/portable_cell_*_housing} は<b>ほぼ真っ白なグレースケール</b>
     * になっている。1.20.1 では濃いグレーで描かれていたので AE2 のものをそのまま借りていたが、
     * そのまま 1.21.1 に持ってくるとポータブルセルが真っ白な塊になってしまう。
     * MEGA は 4.x でも自前の濃い筐体を持っているので、通常セル (mega_*_cell_housing) と揃えて
     * そちらを使う。化学物質用は 1.20.1 の時点で既に MEGA のものを使っていた
     * (AE2 19.2 で汎用の {@code ae2:item/portable_cell_housing} が無くなったため)。</p>
     */
    private static final ResourceLocation PORTABLE_ITEM_HOUSING = mega("megacells", "item/portable_cell_item_housing");
    private static final ResourceLocation PORTABLE_FLUID_HOUSING = mega("megacells", "item/portable_cell_fluid_housing");
    private static final ResourceLocation PORTABLE_HOUSING = mega("megacells", "item/portable_cell_chemical_housing");
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

            // ハウジング → LED → 画面 (layer2 が染色色で塗られる) → 階層色、の順。
            layered("portable_item_cell_" + id,
                    PORTABLE_ITEM_HOUSING, PORTABLE_LED, PORTABLE_ITEM_SCREEN, portableSide(id));
            layered("portable_fluid_cell_" + id,
                    PORTABLE_FLUID_HOUSING, PORTABLE_LED, PORTABLE_FLUID_SCREEN, portableSide(id));
            layered("portable_chemical_cell_" + id,
                    PORTABLE_HOUSING, PORTABLE_LED, PORTABLE_ITEM_SCREEN, portableSide(id));
        }

        simple("creative_cell");
        // 超強化セルは専用のテクスチャを持たず、強化セルの絵を借りる。
        withExistingParent("ultra_creative_cell", mcLoc("item/generated"))
                .texture("layer0", modLoc("item/creative_cell"));

        for (InsaneSpeedCardType card : InsaneSpeedCardType.values()) {
            simple(card.id());
        }
        simple("quantum_acceleration_card");
        simple("task_fusion_card");

        getBuilder("quantum_cpu").parent(new ModelFile.UncheckedModelFile(
                ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID, "block/quantum_cpu")));
        // 実験用CPUは、標準missing-textureモデルをアイテム側でも共有する。
        getBuilder("big_integer_cpu").parent(new ModelFile.UncheckedModelFile(
                ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID, "block/big_integer_cpu")));
        getBuilder("improved_charger").parent(new ModelFile.UncheckedModelFile(
                ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID, "block/improved_charger")));
        getBuilder("insane_interface").parent(new ModelFile.UncheckedModelFile(
                ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID, "block/insane_interface")));
        getBuilder("insane_pattern_provider").parent(new ModelFile.UncheckedModelFile(
                ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID, "block/insane_pattern_provider")));

        // ケーブル版 (プレート) の手持ちモデル。AE2 の部品アイテムの形をそのまま継承し、
        // 正面のテクスチャだけこちらのものに差し替える。
        // ワールドに置いたときの見た目は models/part/*_base.json (手書き) が担当する。
        partItem("insane_interface_part", "insane_interface",
                "ae2:part/interface_sides", "ae2:part/interface_back");
        partItem("insane_pattern_provider_part", "insane_pattern_provider",
                "ae2:part/pattern_provider_sides", "ae2:part/pattern_provider_back");

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
    /**
     * ケーブル版 (プレート) の手持ちモデル。
     *
     * <p>親は AE2 のインターフェイス部品 ({@code ae2:item/cable_interface})。パターンプロバイダ部品も
     * これを親にしてテクスチャだけ差し替えているので、こちらも同じやり方に揃える。</p>
     *
     * @param name        アイテムの登録名
     * @param blockTexture 正面に貼るブロックテクスチャ ({@code insaneae:block/} 以下)
     * @param sides       側面のテクスチャ (AE2 のものを借りる)
     * @param back        背面のテクスチャ (同上)
     */
    private void partItem(String name, String blockTexture, String sides, String back) {
        ItemModelBuilder builder = withExistingParent(name, mega("ae2", "item/part_base"))
                .texture("front", modLoc("block/" + blockTexture))
                .texture("sides", ResourceLocation.parse(sides))
                .texture("back", ResourceLocation.parse(back))
                .texture("particle", modLoc("block/" + blockTexture));
        // 本体の板。
        partPlate(builder, 2, 2, 7, 14, 14, 9, 0, 0, 16, 16);
        // 中央の出っ張り 2 段。
        partPlate(builder, 5, 5, 10, 11, 11, 11, 4, 4, 12, 12);
        partPlate(builder, 5, 5, 9, 11, 11, 10, 4, 4, 12, 12);
    }

    /**
     * ケーブル版アイテムの箱を 1 つ足す。
     *
     * <p>形は {@code ae2:item/cable_interface} と同じだが、<b>正面の uv は差し替えてある</b>。
     * AE2 の {@code part/interface} は部品用に描かれた絵で外周 2px が余白だが、
     * こちらが借りるのは 16x16 のブロック面なので、AE2 と同じ uv で切ると絵が欠ける。
     * ワールド側 ({@code models/part/*_base.json}) も同じ uv にしてあるので、
     * <b>片方だけ変えないこと</b>。</p>
     */
    private static void partPlate(ItemModelBuilder builder,
            int fromX, int fromY, int fromZ, int toX, int toY, int toZ,
            float u0, float v0, float u1, float v1) {
        // 正面以外は uv を書かない = バニラが箱の寸法からそのまま起こす。
        // AE2 は側面用に細い帯を切り出しているが、こちらは借り物のテクスチャなので
        // 自動で十分 (どのみち帯 1px ぶんしか見えない)。
        builder.element()
                .from(fromX, fromY, fromZ).to(toX, toY, toZ)
                .face(Direction.NORTH).uvs(u0, v0, u1, v1).texture("#front").end()
                .face(Direction.SOUTH).texture("#back").end()
                .face(Direction.EAST).texture("#sides").end()
                .face(Direction.WEST).texture("#sides").end()
                .face(Direction.UP).texture("#sides").end()
                .face(Direction.DOWN).texture("#sides").end()
                .end();
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
