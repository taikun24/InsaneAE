package jp.main.taikun.insaneae.datagen;

import jp.main.taikun.insaneae.InsaneAE;
import jp.main.taikun.insaneae.crafting.InsaneAcceleratorType;
import jp.main.taikun.insaneae.energy.InsaneEnergyCellTier;
import jp.main.taikun.insaneae.energy.SolarPanelTier;
import jp.main.taikun.insaneae.upgrade.InsaneSpeedCardType;
import jp.main.taikun.insaneae.crafting.InsaneCraftingUnitType;
import appeng.api.util.AEColor;
import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.model.generators.ItemModelBuilder;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.common.data.ExistingFileHelper;

import java.util.Locale;

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

    /** 超次元 ME ケーブルのテクスチャ (帯・目盛りとも insaneae 側にコピー済み)。 */
    private static ResourceLocation compressedCableTexture(String name) {
        return mega(InsaneAE.MODID, "part/cable/compressed/" + name);
    }

    /** 超次元 ME ケーブルのテクスチャ (帯・目盛りとも insaneae 側にコピー済み)。 */
    private static ResourceLocation hyperCableTexture(String name) {
        return mega(InsaneAE.MODID, "part/cable/hyper/" + name);
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

        // ケーブル版 (プレート) の手持ちモデル。ワールドに置いたときの見た目は
        // models/part/*_base.json (手書き) が担当する。
        partItem("insane_interface_part", "insane_interface",
                mega("ae2", "part/interface_sides"), mega("ae2", "part/interface_back"));
        partItem("insane_pattern_provider_part", "insane_pattern_provider",
                mega("ae2", "part/pattern_provider_sides"), mega("ae2", "part/pattern_provider_back"));

        // 超次元 ME ケーブルの手持ちモデル (17 色)。
        //
        // 形は AE2 の<b>スマートケーブル</b>のアイテムモデル (細い) をそのまま継承し、
        // 帯だけ<b>高密度</b>スマートケーブルのものに差し替える。
        // 「高密度の帯を細いケーブルに巻いてある」= このケーブルそのものの説明になっていて、
        // AE2 のスマートケーブル (帯が違う) とも高密度ケーブル (太い) とも見分けが付く。
        //
        // <b>テクスチャは 3 枚とも insaneae 側にコピーしてある</b>
        // ({@code tools/gen_network_textures.py})。親モデルが差す既定は ae2 のままなので、
        // 帯 (base) だけでなく<b>目盛り 2 枚も明示的に上書きしないと ae2 のものが残る</b>。
        // 以降は AE2 の更新に引きずられずに描き換えられる。
        //
        // <b>色は AE2 のものと同じにしてある。</b>色名がアイテム名になっている以上、
        // 色相を動かすと「白色の…」が白でなくなるうえ、ワールド上の色
        // (AE2 の CableBuilder が AECableType と AEColor だけで決める) とも食い違う。
        //
        // テクスチャのファイル名は AEColor の enum 名そのままで、fluix だけ transparent。
        // registryPrefix (fluix) ではないので注意。
        for (AEColor color : AEColor.values()) {
            withExistingParent(color.registryPrefix + "_hyper_cable",
                    mega("ae2", "item/smart_cable_base"))
                    .texture("base", hyperCableTexture(color.name().toLowerCase(Locale.ROOT)))
                    .texture("channelsOdd", hyperCableTexture("channels_00"))
                    .texture("channelsEven", hyperCableTexture("channels_10"));
        }

        // 圧縮 ME 高密度スマートケーブルの手持ちモデル (17 色)。組み方は上と同じで、
        // テクスチャだけ<b>枠を明るくした版</b>を差す。同じ下敷きから作っているので、
        // ここを hyper と同じにすると<b>2 本の見分けが付かなくなる</b>。
        for (AEColor color : AEColor.values()) {
            withExistingParent(color.registryPrefix + "_compressed_dense_cable",
                    mega("ae2", "item/smart_cable_base"))
                    .texture("base", compressedCableTexture(color.name().toLowerCase(Locale.ROOT)))
                    .texture("channelsOdd", compressedCableTexture("channels_00"))
                    .texture("channelsEven", compressedCableTexture("channels_10"));
        }

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
     * <p>形は {@code ae2:item/cable_interface} と同じだが、<b>正面の uv は差し替えてある</b>。
     * AE2 の {@code part/interface} は部品用に描かれた絵で外周 2px が余白だが、
     * こちらが借りるのは 16x16 のブロック面なので、AE2 と同じ uv で切ると絵が欠ける。
     * ワールド側 ({@code models/part/*_base.json}) も同じ uv にしてあるので、
     * <b>片方だけ変えないこと</b>。</p>
     */
    private void partItem(String name, String blockTexture, ResourceLocation sides,
            ResourceLocation back) {
        ItemModelBuilder builder = withExistingParent(name, mega("ae2", "item/part_base"))
                .texture("front", modLoc("block/" + blockTexture))
                .texture("sides", sides)
                .texture("back", back)
                .texture("particle", modLoc("block/" + blockTexture));
        partPlate(builder, 2, 2, 7, 14, 14, 9, 0, 0, 16, 16);
        partPlate(builder, 5, 5, 10, 11, 11, 11, 4, 4, 12, 12);
        partPlate(builder, 5, 5, 9, 11, 11, 10, 4, 4, 12, 12);
    }

    /** ケーブル版アイテムの箱を 1 つ足す。正面以外の uv はバニラに任せる。 */
    private static void partPlate(ItemModelBuilder builder,
            int fromX, int fromY, int fromZ, int toX, int toY, int toZ,
            float u0, float v0, float u1, float v1) {
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
