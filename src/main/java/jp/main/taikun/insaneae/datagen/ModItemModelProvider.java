package jp.main.taikun.insaneae.datagen;

import jp.main.taikun.insaneae.InsaneAE;
import jp.main.taikun.insaneae.crafting.InsaneAcceleratorType;
import jp.main.taikun.insaneae.energy.InsaneEnergyCellTier;
import jp.main.taikun.insaneae.energy.SolarPanelTier;
import jp.main.taikun.insaneae.upgrade.InsaneSpeedCardType;
import jp.main.taikun.insaneae.crafting.InsaneCraftingUnitType;
import jp.main.taikun.insaneae.integration.appflux.FluxTiers;
import appeng.api.util.AEColor;
import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.generators.ItemModelBuilder;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.Locale;

/**
 * アイテムモデル。
 *
 * <p>セル系はAE2 / MEGA Cellsと同じ見た目の続きにする。階層で色が変わる部分だけ
 * {@code tools/gen_cell_textures.py}が階層色から生成し、セルコンポーネント、通常セルの窓と帯、
 * ポータブルセルの側面)、階層に依らないハウジング・LED・画面はMEGA / AE2のものを
 * レイヤで重ねる。生成側のマスクは借りているハウジングのドット位置に合わせてあるので、
 * ハウジングを差し替えるならマスクも描き直すこと。</p>
 *
 * <p>レイヤ番号には意味がある。{@code item/generated} はlayerNにtintindex Nを振り、
 * AE2の色ハンドラ ({@code BasicStorageCell#getColor} / {@code AbstractPortableCell#getColor}) が
 * tintindex 1を中身の量の色、ポータブルはさらにtintindex 2を画面の色として塗る。
 * したがってレイヤの順番はAE2 / MEGA本体のモデルと1ドットも違えてはならない。</p>
 *
 * <p>ポータブルセルの順番はハウジング、LED、画面、側面で、
 * AE2 19.2の {@code ae2:item/portable_item_cell_1k} がこの順になっている。
 * 画面を先頭にしてハウジングを後ろに置くと、不透明なハウジングが
 * 一番上に来て他のレイヤを全部隠し、そのうえ染色色で塗られる。既定の染色色は白なので、
 * ポータブルセルが真っ白な塊になる。</p>
 *
 * <p>化学物質セル / FEセルはApplied Mekanistics / AppliedFlux未導入だとアイテム自体が登録されないが、
 * モデルは名前だけで生成できるので常に出力しておく。使われないだけで害はない。</p>
 */
public class ModItemModelProvider extends ItemModelProvider {

    private static final ModelFile GENERATED = new ModelFile.UncheckedModelFile("item/generated");

    private static final ResourceLocation CELL_LED = mega("ae2", "item/storage_cell_led");
    private static final ResourceLocation PORTABLE_LED = mega("ae2", "item/portable_cell_led");
    /** 通常セルのハウジング。MEGAの1M〜256Mと同じ見た目の続きにする。 */
    private static final ResourceLocation ITEM_HOUSING = mega("megacells", "item/mega_item_cell_housing");
    private static final ResourceLocation FLUID_HOUSING = mega("megacells", "item/mega_fluid_cell_housing");
    private static final ResourceLocation CHEMICAL_HOUSING = mega("megacells", "item/mega_chemical_cell_housing");
    /**
     * ポータブルセルの画面部分。
     *
     * <p>MEGA Cells 4.xのポータブルセルはAE2の汎用テクスチャ1枚を使っているので、
     * アイテム用と液体用の両方をそれに合わせる。</p>
     */
    //? if >=1.21 {
    private static final ResourceLocation PORTABLE_ITEM_SCREEN = mega("ae2", "item/portable_cell_screen");
    private static final ResourceLocation PORTABLE_FLUID_SCREEN = PORTABLE_ITEM_SCREEN;
    /**
     * ポータブルセルの筐体。MEGAのものを使う。AE2のものではない。
     *
     * <p>AE2 19.2の {@code ae2:item/portable_cell_*_housing} はほぼ真っ白なグレースケール
     * になっているので、借りるとポータブルセルが真っ白な塊になる。
     * MEGAは4.xでも自前の濃い筐体を持っているので、通常セル (mega_*_cell_housing) と揃えて
     * そちらを使う。化学物質用はAE2 19.2に汎用の {@code ae2:item/portable_cell_housing} が
     * 無いため、同じくMEGAのものを使う。</p>
     */
    private static final ResourceLocation PORTABLE_ITEM_HOUSING = mega("megacells", "item/portable_cell_item_housing");
    private static final ResourceLocation PORTABLE_FLUID_HOUSING = mega("megacells", "item/portable_cell_fluid_housing");
    private static final ResourceLocation PORTABLE_HOUSING = mega("megacells", "item/portable_cell_chemical_housing");
    /**
     * AppliedFlux連携のFEセルの筐体。AppliedFluxのものを借りる。
     *
     * <p>AppliedFlux 2.1.xのFEセルは、MEGA 4.xと同じ形の窓と同じ3階調の色を使っているので、
     * 階層色レイヤ ({@code standardCell} / {@code portableSide}) はそのまま重なる
     * jarのテクスチャと1ドットずつ突き合わせて確認済み。</p>
     */
    private static final ResourceLocation FE_HOUSING = mega("appflux", "item/fe_cell_housing");
    private static final ResourceLocation PORTABLE_FE_HOUSING = mega("appflux", "item/portable_cell_fe_housing");
    //?} else {
    /*private static final ResourceLocation PORTABLE_ITEM_SCREEN =
            mega("megacells", "item/cell/portable/portable_cell_item_screen");
    private static final ResourceLocation PORTABLE_FLUID_SCREEN =
            mega("megacells", "item/cell/portable/portable_cell_fluid_screen");
    private static final ResourceLocation PORTABLE_ITEM_HOUSING = mega("ae2", "item/portable_cell_item_housing");
    private static final ResourceLocation PORTABLE_FLUID_HOUSING = mega("ae2", "item/portable_cell_fluid_housing");
    /^* 化学物質のポータブルセル用。AE2 に専用の筐体が無いので汎用のものを使う。 ^/
    private static final ResourceLocation PORTABLE_HOUSING = mega("ae2", "item/portable_cell_housing");
    /^*
     * AppliedFlux連携のFEセルの筐体と画面。AppliedFluxの1M〜256Mは、MEGA版のものを借りる。
     *
     * <p>筐体の形はMEGAの化学物質セル筐体と同じなので、階層色レイヤ ({@code standardCell}) が
     * そのまま重なる。jarのテクスチャで確認済み。ポータブルもAppliedFlux自身がAE2の側面帯を
     * 重ねているので {@code portableSide} がそのまま合う。</p>
     ^/
    private static final ResourceLocation FE_HOUSING = mega("appflux", "item/mega_fe_cell_housing");
    private static final ResourceLocation PORTABLE_FE_SCREEN = mega("appflux", "item/portable_mega_cell_screen");
    private static final ResourceLocation PORTABLE_FE_HOUSING = mega("appflux", "item/portable_mega_cell_fe_housing");
    *///?}
    /**
     * AE2 Things連携のDISKの下地。窓がMEGAのハウジングと同じ形・同じ3階調なので、
     * 通常セルの階層色レイヤ ({@code standardCell}) がそのまま重なって1kの色を覆う。
     */
    private static final ResourceLocation DISK_BASE = mega("ae2things", "item/disk_drive_1k");
    /** AE2 MEGA Thingsの液体・化学物質DISKの下地。窓はアイテムDISKと同じ位置で、階層色レイヤがそのまま重なる。 */
    private static final ResourceLocation FLUID_DISK_BASE = mega("ae2_mega_things", "item/fluid_disk_drive_1k");
    private static final ResourceLocation CHEMICAL_DISK_BASE = mega("ae2_mega_things", "item/chemical_disk_drive_1k");
    private static final ResourceLocation SPEED_CARD = mega("ae2", "item/card_speed");
    /** AE2の {@code InitItemModelsProperties} が登録するエネルギーセルの残量プロパティ。 */
    private static final ResourceLocation ENERGY_FILL_LEVEL = mega("ae2", "fill_level");

    private static ResourceLocation mega(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
    //? if <1.21 {
    /*private static ResourceLocation compressedCableTexture(String name) {
        return mega(InsaneAE.MODID, "part/cable/compressed/" + name);
    }
    *///?}

    /** 超次元MEケーブルのテクスチャ。帯・目盛りともinsaneae側にコピー済み。 */
    private static ResourceLocation hyperCableTexture(String name) {
        return mega(InsaneAE.MODID, "part/cable/hyper/" + name);
    //? if >=1.21 {
    }

    /** 圧縮ME高密度スマートケーブルのテクスチャ。枠を明るくした版。 */
    private static ResourceLocation compressedCableTexture(String name) {
        return mega(InsaneAE.MODID, "part/cable/compressed/" + name);
    //?}
    }

    public ModItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, InsaneAE.MODID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        for (InsaneCraftingUnitType tier : InsaneCraftingUnitType.values()) {
            String id = tier.id();

            // クラフトストレージのアイテムは未formedのブロックモデルをそのまま使う
            getBuilder(tier.blockId()).parent(new ModelFile.UncheckedModelFile(
                    ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID, "block/crafting/" + id + "_storage")));

            layered(tier.cellComponentId(), component(id));

            // ハウジング、LED、階層色の順で重ねる。LEDのlayer1は中身の量で着色される
            layered("item_storage_cell_" + id, ITEM_HOUSING, CELL_LED, standardCell(id));
            layered("fluid_storage_cell_" + id, FLUID_HOUSING, CELL_LED, standardCell(id));
            layered("chemical_storage_cell_" + id, CHEMICAL_HOUSING, CELL_LED, standardCell(id));
            layered("disk_drive_" + id, DISK_BASE, CELL_LED, standardCell(id));
            if (tier.compareTo(InsaneCraftingUnitType.STORAGE_16P) <= 0) {
                layered("fluid_disk_drive_" + id, FLUID_DISK_BASE, CELL_LED, standardCell(id));
                layered("chemical_disk_drive_" + id, CHEMICAL_DISK_BASE, CELL_LED, standardCell(id));
            }

            layered("portable_item_cell_" + id,
                    //? if >=1.21 {
                    PORTABLE_ITEM_HOUSING, PORTABLE_LED, PORTABLE_ITEM_SCREEN, portableSide(id));
                    //?} else {
                    /*PORTABLE_ITEM_SCREEN, PORTABLE_LED, PORTABLE_ITEM_HOUSING, portableSide(id));
                    *///?}
            layered("portable_fluid_cell_" + id,
                    //? if >=1.21 {
                    PORTABLE_FLUID_HOUSING, PORTABLE_LED, PORTABLE_FLUID_SCREEN, portableSide(id));
                    //?} else {
                    /*PORTABLE_FLUID_SCREEN, PORTABLE_LED, PORTABLE_FLUID_HOUSING, portableSide(id));
                    *///?}
            layered("portable_chemical_cell_" + id,
                    //? if >=1.21 {
                    PORTABLE_HOUSING, PORTABLE_LED, PORTABLE_ITEM_SCREEN, portableSide(id));
                    //?} else {
                    /*PORTABLE_ITEM_SCREEN, PORTABLE_LED, PORTABLE_HOUSING, portableSide(id));
                    *///?}
        }

        // FEセルはAppliedFluxの容量計算がlongなので16Tまでしか出さない (FluxTiers)
        for (InsaneCraftingUnitType tier : FluxTiers.TIERS) {
            String id = tier.id();
            layered("energy_component_" + id, modLoc("item/energy_component_" + id));
            layered("fe_storage_cell_" + id, FE_HOUSING, CELL_LED, standardCell(id));
            layered("portable_fe_cell_" + id,
                    //? if >=1.21 {
                    PORTABLE_FE_HOUSING, PORTABLE_LED, PORTABLE_ITEM_SCREEN, portableSide(id));
                    //?} else {
                    /*PORTABLE_FE_SCREEN, PORTABLE_LED, PORTABLE_FE_HOUSING, portableSide(id));
                    *///?}
        }

        simple("creative_cell");
        // 超強化セルは専用のテクスチャを持たず、強化セルの絵を借りる
        withExistingParent("ultra_creative_cell", mcLoc("item/generated"))
                .texture("layer0", modLoc("item/creative_cell"));
        // 超次元MEストレージセルは仮のテクスチャ (強化セルの絵を紫に寄せたもの)
        withExistingParent("insane_me_cell", mcLoc("item/generated"))
                .texture("layer0", modLoc("item/insane_me_cell"));

        for (InsaneSpeedCardType card : InsaneSpeedCardType.values()) {
            simple(card.id());
        }
        simple("insane_processor_press");
        simple("printed_insane_processor");
        simple("insane_processor");
        simple("quantum_acceleration_card");
        simple("task_fusion_card");

        getBuilder("quantum_cpu").parent(new ModelFile.UncheckedModelFile(
                ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID, "block/quantum_cpu")));
        // 実験用CPUは、標準missing-textureモデルをアイテム側でも共有する
        getBuilder("big_integer_cpu").parent(new ModelFile.UncheckedModelFile(
                ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID, "block/big_integer_cpu")));
        getBuilder("improved_charger").parent(new ModelFile.UncheckedModelFile(
                ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID, "block/improved_charger")));
        getBuilder("insane_interface").parent(new ModelFile.UncheckedModelFile(
                ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID, "block/insane_interface")));
        getBuilder("insane_pattern_provider").parent(new ModelFile.UncheckedModelFile(
                ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID, "block/insane_pattern_provider")));
        getBuilder("gamertaikun_doll").parent(new ModelFile.UncheckedModelFile(
                ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID, "block/gamertaikun_doll")));

        // ケーブル版のプレートの手持ちモデル。AE2の部品アイテムの形をそのまま継承し、
        // 正面のテクスチャだけこちらのものに差し替える
        // ワールドに置いたときの見た目はmodels/part/*_base.jsonが担当する。手書きである
        partItem("insane_interface_part", "insane_interface",
                //? if >=1.21 {
                "ae2:part/interface_sides", "ae2:part/interface_back");
                //?} else {
                /*mega("ae2", "part/interface_sides"), mega("ae2", "part/interface_back"));
                *///?}
        partItem("insane_pattern_provider_part", "insane_pattern_provider",
                //? if >=1.21 {
                "ae2:part/pattern_provider_sides", "ae2:part/pattern_provider_back");
                //?} else {
                /*mega("ae2", "part/pattern_provider_sides"), mega("ae2", "part/pattern_provider_back"));
                *///?}

        // 超次元MEケーブルの手持ちモデル。17色ある
        //
        // 形はAE2のスマートケーブルのアイテムモデルを、細いままそのまま継承し、
        // 帯だけ高密度スマートケーブルのものに差し替える
        // 「高密度の帯を細いケーブルに巻いてある」= このケーブルそのものの説明になっていて、
        // AE2のスマートケーブルとは帯が違い、高密度ケーブルとは太さが違うので見分けが付く
        //
        // テクスチャは3枚ともinsaneae側にコピーしてある
        // ({@code tools/gen_network_textures.py})。親モデルが差す既定はae2のままなので、
        // 帯 (base) だけでなく目盛り2枚も明示的に上書きしないとae2のものが残る
        // 以降はAE2の更新に引きずられずに描き換えられる
        //
        // 3枚は同じ一族、高密度スマートから取ること。帯は目盛りが乗る溝を
        // 透明のまま空けてあるので、細いスマート側の目盛りと混ぜると、溝の位置がずれる
        // 埋まらずに手持ちの絵に穴が空く
        //
        // 色はAE2のものと同じにしてある。色名がアイテム名になっている以上、
        // 色相を動かすと「白色の…」が白でなくなるうえ、ワールド上の色
        // AE2のCableBuilderがAECableTypeとAEColorだけで決める形とも食い違う
        //
        // テクスチャのファイル名はAEColorのenum名そのままで、fluixだけtransparent
        // registryPrefix (fluix) ではないので注意
        for (AEColor color : AEColor.values()) {
            withExistingParent(color.registryPrefix + "_hyper_cable",
                    mega("ae2", "item/smart_cable_base"))
                    .texture("base", hyperCableTexture(color.name().toLowerCase(Locale.ROOT)))
                    .texture("channelsOdd", hyperCableTexture("channels_00"))
                    .texture("channelsEven", hyperCableTexture("channels_10"));
        }

        // 超次元MEケーブル (無制限) の手持ちモデル。絵は種類ごとの絵を使う
        for (AEColor color : AEColor.values()) {
            withExistingParent(color.registryPrefix + "_insane_cable",
                    mega("ae2", "item/smart_cable_base"))
                    .texture("base", mega(InsaneAE.MODID, "part/cable/insane/" + color.name().toLowerCase(Locale.ROOT)))
                    .texture("channelsOdd", mega(InsaneAE.MODID, "part/cable/insane/channels_00"))
                    .texture("channelsEven", mega(InsaneAE.MODID, "part/cable/insane/channels_10"));
        }

        // 圧縮ME高密度スマートケーブルの手持ちモデル。17色あり、組み方は上と同じで、
        // テクスチャだけ枠を明るくした版を差す。同じ下敷きから作っているので、
        // ここをhyperと同じにすると2本の見分けが付かなくなる
        // ワールド上はどちらもAE2のスマートケーブルの絵になるため、
        // 見分けられるのは手持ちの絵だけ)
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
     * <p>AE2は {@code EnergyCellBlockItem} をregistryから拾って
     * {@code ae2:fill_level}を登録し、現在値と最大値を渡すので、
     * ブロックと同じ5段階のモデルをoverrideで切り替えれば残量が見た目に出る。</p>
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
     * ケーブル版のプレートの手持ちモデル。
     *
     * <p>親はAE2のインターフェイス部品 ({@code ae2:item/cable_interface})。パターンプロバイダ部品も
     * これを親にしてテクスチャだけ差し替えているので、こちらも同じやり方に揃える。</p>
     *
     * @param nameアイテムの登録名
     * @param blockTexture正面に貼るブロックテクスチャ。{@code insaneae:block/}以下
     * @param sides側面のテクスチャ。AE2のものを借りる
     * @param back背面のテクスチャ。sidesと同じくAE2のものを借りる
     */
    //? if >=1.21 {
    private void partItem(String name, String blockTexture, String sides, String back) {
    //?} else {
    /*private void partItem(String name, String blockTexture, ResourceLocation sides,
            ResourceLocation back) {
    *///?}
        ItemModelBuilder builder = withExistingParent(name, mega("ae2", "item/part_base"))
                .texture("front", modLoc("block/" + blockTexture))
                //? if >=1.21 {
                .texture("sides", ResourceLocation.parse(sides))
                .texture("back", ResourceLocation.parse(back))
                //?} else {
                /*.texture("sides", sides)
                .texture("back", back)
                *///?}
                .texture("particle", modLoc("block/" + blockTexture));
        partPlate(builder, 2, 2, 7, 14, 14, 9, 0, 0, 16, 16);
        partPlate(builder, 5, 5, 10, 11, 11, 11, 4, 4, 12, 12);
        partPlate(builder, 5, 5, 9, 11, 11, 10, 4, 4, 12, 12);
    }

    /**
     * ケーブル版アイテムの箱を1つ足す。
     *
     * <p>形は {@code ae2:item/cable_interface} と同じだが、正面のuvは差し替えてある。
     * AE2の {@code part/interface} は部品用に描かれた絵で外周2pxが余白だが、
     * こちらが借りるのは16x16のブロック面なので、AE2と同じuvで切ると絵が欠ける。
     * ワールド側 ({@code models/part/*_base.json}) も同じuvにしてあるので、
     * 片方だけ変えないこと。</p>
     */
    private static void partPlate(ItemModelBuilder builder,
            int fromX, int fromY, int fromZ, int toX, int toY, int toZ,
            float u0, float v0, float u1, float v1) {
        // 正面以外はuvを書かない = バニラが箱の寸法からそのまま起こす
        // AE2は側面用に細い帯を切り出しているが、こちらは借り物のテクスチャなので
        // 自動で十分。どのみち帯1pxぶんしか見えない
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

    /** 同上、通常セルの階層色レイヤ。ハウジングの窓と左面の帯だけを描いたもの。 */
    private static ResourceLocation standardCell(String tier) {
        return mega(InsaneAE.MODID, "item/cell/standard/storage_cell_" + tier);
    }

    /** 同上、ポータブルセルの側面。階層色の帯だけを描いたレイヤ。 */
    private static ResourceLocation portableSide(String tier) {
        return mega(InsaneAE.MODID, "item/cell/portable/portable_cell_side_" + tier);
    }

    private void layered(String name, ResourceLocation... layers) {
        ItemModelBuilder builder = getBuilder(name).parent(GENERATED);
        for (int layer = 0; layer < layers.length; layer++) {
            builder.texture("layer" + layer, layers[layer]);
        }
    }
}
