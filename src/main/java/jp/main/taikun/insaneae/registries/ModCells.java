package jp.main.taikun.insaneae.registries;

import appeng.api.stacks.AEKeyType;
import appeng.items.storage.StorageTier;
import appeng.menu.me.common.MEStorageMenu;
import gripe._90.megacells.definition.MEGAItems;
import jp.main.taikun.insaneae.InsaneAE;
import jp.main.taikun.insaneae.cell.InsanePortableCellItem;
import jp.main.taikun.insaneae.cell.InsaneCreativeCellItem;
import jp.main.taikun.insaneae.cell.InsaneMeCellItem;
import jp.main.taikun.insaneae.cell.InsaneUltraCreativeCellItem;
import jp.main.taikun.insaneae.cell.InsaneStorageCellItem;
import jp.main.taikun.insaneae.crafting.InsaneCraftingUnitType;
import jp.main.taikun.insaneae.util.TieredNames;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
//? if >=1.21 {
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
//?}
//? if <1.21 {
/*import net.neoforged.neoforge.registries.ForgeRegistries;
import net.neoforged.neoforge.registries.RegistryObject;
*///?}

/**
 * 各階層のMEストレージセル。通常セルとポータブルセル。
 *
 * <p>ハウジングはMEGA Cellsのものを使う。インベントリ内で右クリックして分解すると
 * ハウジングと同階層のセルコンポーネントに戻る。</p>
 *
 * <p>化学物質セルはApplied Mekanisticsが入っているときだけ
 * {@code jp.main.taikun.insaneae.integration.appmek.AppMekCells} が追加する。</p>
 */
public class ModCells {
    private static final DeferredRegister<Item> ITEMS =
            //? if >=1.21 {
            DeferredRegister.create(Registries.ITEM, InsaneAE.MODID);
            //?} else {
            /*DeferredRegister.create(ForgeRegistries.ITEMS, InsaneAE.MODID);
            *///?}

    /** 型ごとに予約されるバイト数。容量に対して十分小さい固定値で足りる。 */
    public static final int BYTES_PER_TYPE = 8192;
    /** 最下段 (1G) の型数のビット幅。2^6-1 = 63でAE2の既定上限と同じ。 */
    private static final int BASE_TYPE_BITS = 6;
    /** 最上段 (8E) の型数のビット幅。2^31-1 = 約21億。{@code int} の上限。 */
    private static final int MAX_TYPE_BITS = 31;
    /** 最下段 (1G) のアイドル消費。1階層ごとに0.5ずつ増やす。 */
    private static final double BASE_IDLE_DRAIN = 5.0;
    /** MEGA Cellsの階層indexの続きから振る。1M〜256Mは6〜10。 */
    private static final int TIER_INDEX_OFFSET = 11;

    /**
     * ポータブルセルの画面の色、つまり上面の色。種類に依らず全部これ。
     *
     * <p>{@code AbstractPortableCell#getColor}がこの値をtintindex 2に塗る。tintindex 2は画面レイヤ。
     * AE2 19.2・MEGA Cells 4.11・Applied Mekanistics 1.6.3はいずれも種類によらず
     * この水色1色、定数{@code 8440575}を渡しているので、それに揃える
     * 3つのjarで確認済み。暗い色、たとえば{@code 0x353535}を渡すと画面が黒く見える。</p>
     *
     * <p>種類の区別は画面の色ではなく筐体テクスチャのほうで付いている
     * ({@code megacells:item/portable_cell_item/fluid/chemical_housing})。</p>
     */
    //? if >=1.21 {
    public static final int PORTABLE_SCREEN_COLOR = 0x80CFFF;
    //?}

    public static final Map<InsaneCraftingUnitType, StorageTier> TIERS =
            new EnumMap<>(InsaneCraftingUnitType.class);
    //? if >=1.21 {
    public static final Map<InsaneCraftingUnitType, DeferredHolder<Item, Item>> ITEM_CELLS =
    //?} else {
    /*public static final Map<InsaneCraftingUnitType, RegistryObject<Item>> ITEM_CELLS =
    *///?}
            new EnumMap<>(InsaneCraftingUnitType.class);
    //? if >=1.21 {
    public static final Map<InsaneCraftingUnitType, DeferredHolder<Item, Item>> FLUID_CELLS =
    //?} else {
    /*public static final Map<InsaneCraftingUnitType, RegistryObject<Item>> FLUID_CELLS =
    *///?}
            new EnumMap<>(InsaneCraftingUnitType.class);
    //? if >=1.21 {
    public static final Map<InsaneCraftingUnitType, DeferredHolder<Item, Item>> PORTABLE_ITEM_CELLS =
    //?} else {
    /*public static final Map<InsaneCraftingUnitType, RegistryObject<Item>> PORTABLE_ITEM_CELLS =
    *///?}
            new EnumMap<>(InsaneCraftingUnitType.class);
    //? if >=1.21 {
    public static final Map<InsaneCraftingUnitType, DeferredHolder<Item, Item>> PORTABLE_FLUID_CELLS =
    //?} else {
    /*public static final Map<InsaneCraftingUnitType, RegistryObject<Item>> PORTABLE_FLUID_CELLS =
    *///?}
            new EnumMap<>(InsaneCraftingUnitType.class);

    /**
     * 強化クリエイティブセル。設定した中身を無限に供給するのはAE2のものと同じで、
     * 報告する在庫量が21億 ({@code int}) ではなく約922京 ({@code long})。
     * 種類の制限は無いので1個でアイテムも液体も化学物質もまかなう。
     */
    //? if >=1.21 {
    public static final DeferredHolder<Item, Item> CREATIVE_CELL =
    //?} else {
    /*public static final RegistryObject<Item> CREATIVE_CELL =
    *///?}
            register("creative_cell", () -> new InsaneCreativeCellItem(new Item.Properties()));

    /**
     * 超強化クリエイティブセル。報告する在庫量がBigIntegerになる。
     *
     * <p>AE2から見える量は強化クリエイティブセルと同じ922京のまま
     * {@code KeyCounter}がlongなので、そこは上げようがない。差が出るのは
     * ACOの正確な計算経路だけで、922京を超える材料を要求するBigIntegerクラフトを
     * 作成可能にするためのもの。ACOが無ければ強化クリエイティブセルと同じ。</p>
     */
    //? if >=1.21 {
    public static final DeferredHolder<Item, Item> ULTRA_CREATIVE_CELL =
    //?} else {
    /*public static final RegistryObject<Item> ULTRA_CREATIVE_CELL =
    *///?}
            register("ultra_creative_cell", () -> new InsaneUltraCreativeCellItem(new Item.Properties()));

    /**
     * 超次元MEセル。アイテム・液体・化学物質を種類も量も制限なしに格納する。
     * 中身は BigInteger で持ち、AE2 へは 1 種あたり Long.MAX_VALUE まで報告する。
     */
    //? if >=1.21 {
    public static final DeferredHolder<Item, Item> INSANE_ME_CELL =
    //?} else {
    /*public static final RegistryObject<Item> INSANE_ME_CELL =
    *///?}
            register("insane_me_cell", () -> new InsaneMeCellItem(new Item.Properties()));

    static {
        InsaneCraftingUnitType[] tiers = InsaneCraftingUnitType.values();
        for (int index = 0; index < tiers.length; index++) {
            InsaneCraftingUnitType tier = tiers[index];
            double idleDrain = idleDrain(tier);
            // ItemLike / Supplierは単一メソッドなので、登録順に依存しないよう遅延解決させる
            ItemLike component = () -> ModItems.CELL_COMPONENTS.get(tier).get();
            StorageTier storageTier = new StorageTier(TIER_INDEX_OFFSET + index, tier.id(),
                    clampBytes(tier.getStorageBytes()), idleDrain, component::asItem);
            TIERS.put(tier, storageTier);

            // 表示名は階層ごとのlangキーではなく、「書式キー + 階層ラベル」で作る。TieredNamesを参照
            ITEM_CELLS.put(tier, register("item_storage_cell_" + tier.id(),
                    () -> new InsaneStorageCellItem(new Item.Properties().stacksTo(1), component,
                            MEGAItems.MEGA_ITEM_CELL_HOUSING, idleDrain, tier.getStorageBytes(),
                            BYTES_PER_TYPE, totalTypes(tier), AEKeyType.items(),
                            TieredNames.ITEM_STORAGE_CELL, tier.label())));
            FLUID_CELLS.put(tier, register("fluid_storage_cell_" + tier.id(),
                    () -> new InsaneStorageCellItem(new Item.Properties().stacksTo(1), component,
                            MEGAItems.MEGA_FLUID_CELL_HOUSING, idleDrain, tier.getStorageBytes(),
                            BYTES_PER_TYPE, totalTypes(tier), AEKeyType.fluids(),
                            TieredNames.FLUID_STORAGE_CELL, tier.label())));

            PORTABLE_ITEM_CELLS.put(tier, register("portable_item_cell_" + tier.id(),
                    () -> new InsanePortableCellItem(new Item.Properties(), storageTier,
                            tier.getStorageBytes(), totalTypes(tier), AEKeyType.items(),
                            //? if >=1.21 {
                            MEStorageMenu.PORTABLE_ITEM_CELL_TYPE, PORTABLE_SCREEN_COLOR,
                            //?} else {
                            /*MEStorageMenu.PORTABLE_ITEM_CELL_TYPE, 0x353535,
                            *///?}
                            TieredNames.PORTABLE_ITEM_CELL, tier.label())));
            PORTABLE_FLUID_CELLS.put(tier, register("portable_fluid_cell_" + tier.id(),
                    () -> new InsanePortableCellItem(new Item.Properties(), storageTier,
                            tier.getStorageBytes(), totalTypes(tier), AEKeyType.fluids(),
                            //? if >=1.21 {
                            MEStorageMenu.PORTABLE_FLUID_CELL_TYPE, PORTABLE_SCREEN_COLOR,
                            //?} else {
                            /*MEStorageMenu.PORTABLE_FLUID_CELL_TYPE, 0xF1C5,
                            *///?}
                            TieredNames.PORTABLE_FLUID_CELL, tier.label())));
        }
    }

    /**
     * その階層のセルが扱える型、つまりアイテムの種類の数。
     *
     * <p>最下段の63、AE2の既定上限から、最上段の{@code 2^31-1}、約21億まで、
     * 階層ごとにビット幅を均等に広げる。18階層で6 bitから31 bitなので、約1.5 bit/段。</p>
     *
     * <p>AE2の {@code BasicCellInventory} は63で頭打ちにするので、
     * {@code BasicCellInventoryMixin} が {@link jp.main.taikun.insaneae.cell.IHugeCellItem}
     * なセルに限ってその制限を外している。型1つあたり {@link #BYTES_PER_TYPE} バイトを
     * 消費するのは変わらないので、実際に使い切れるかは容量次第。</p>
     */
    public static int totalTypes(InsaneCraftingUnitType tier) {
        int last = InsaneCraftingUnitType.values().length - 1;
        int bits = BASE_TYPE_BITS + (MAX_TYPE_BITS - BASE_TYPE_BITS) * tier.ordinal() / last;
        return (int) ((1L << bits) - 1);
    }

    /** 1階層ごとに0.5ずつ増えるアイドル消費。 */
    public static double idleDrain(InsaneCraftingUnitType tier) {
        return BASE_IDLE_DRAIN + 0.5 * tier.ordinal();
    }

    /**
     * {@link StorageTier} / {@code BasicStorageCell} が要求するint版の容量。
     * 実容量は {@code IHugeCellItem} 経由でlongのまま扱われるので、これは保険値。
     */
    public static int clampBytes(long totalBytes) {
        return (int) Math.min(Integer.MAX_VALUE, totalBytes);
    }

    /** 化学物質セルなど、他クラスからアイテムを追加するための入口。 */
    //? if >=1.21 {
    public static DeferredHolder<Item, Item> register(String name, Supplier<Item> factory) {
    //?} else {
    /*public static RegistryObject<Item> register(String name, Supplier<Item> factory) {
    *///?}
        return ITEMS.register(name, factory);
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
