package jp.main.taikun.insaneae.registries;

import net.minecraft.core.registries.Registries;
import appeng.api.stacks.AEKeyType;
import appeng.items.storage.StorageTier;
import appeng.menu.me.common.MEStorageMenu;
import gripe._90.megacells.definition.MEGAItems;
import jp.main.taikun.insaneae.InsaneAE;
import jp.main.taikun.insaneae.cell.InsanePortableCellItem;
import jp.main.taikun.insaneae.cell.InsaneCreativeCellItem;
import jp.main.taikun.insaneae.cell.InsaneStorageCellItem;
import jp.main.taikun.insaneae.crafting.InsaneCraftingUnitType;
import jp.main.taikun.insaneae.util.TieredNames;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 各階層の ME ストレージセル (通常セル / ポータブルセル)。
 *
 * <p>ハウジングは MEGA Cells のものを使う。分解 (インベントリ内で右クリック) すると
 * ハウジングと同階層のセルコンポーネントに戻る。</p>
 *
 * <p>化学物質セルは Applied Mekanistics が入っているときだけ
 * {@code jp.main.taikun.insaneae.integration.appmek.AppMekCells} が追加する。</p>
 */
public class ModCells {
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, InsaneAE.MODID);

    /** 型ごとに予約されるバイト数。容量に対して十分小さい固定値で足りる。 */
    public static final int BYTES_PER_TYPE = 8192;
    /** 最下段 (1G) の型数のビット幅。2^6-1 = 63 で AE2 の既定上限と同じ。 */
    private static final int BASE_TYPE_BITS = 6;
    /** 最上段 (8E) の型数のビット幅。2^31-1 = 約 21 億 ({@code int} の上限)。 */
    private static final int MAX_TYPE_BITS = 31;
    /** 最下段 (1G) のアイドル消費。1 階層ごとに 0.5 ずつ増やす。 */
    private static final double BASE_IDLE_DRAIN = 5.0;
    /** MEGA Cells の階層 index (1M〜256M = 6〜10) の続きから振る。 */
    private static final int TIER_INDEX_OFFSET = 11;

    public static final Map<InsaneCraftingUnitType, StorageTier> TIERS =
            new EnumMap<>(InsaneCraftingUnitType.class);
    public static final Map<InsaneCraftingUnitType, DeferredHolder<Item, Item>> ITEM_CELLS =
            new EnumMap<>(InsaneCraftingUnitType.class);
    public static final Map<InsaneCraftingUnitType, DeferredHolder<Item, Item>> FLUID_CELLS =
            new EnumMap<>(InsaneCraftingUnitType.class);
    public static final Map<InsaneCraftingUnitType, DeferredHolder<Item, Item>> PORTABLE_ITEM_CELLS =
            new EnumMap<>(InsaneCraftingUnitType.class);
    public static final Map<InsaneCraftingUnitType, DeferredHolder<Item, Item>> PORTABLE_FLUID_CELLS =
            new EnumMap<>(InsaneCraftingUnitType.class);

    /**
     * 強化クリエイティブセル。設定した中身を無限に供給するのは AE2 のものと同じで、
     * 報告する在庫量が 21 億 ({@code int}) ではなく約 922 京 ({@code long})。
     * 種類の制限は無いので 1 個でアイテムも液体も化学物質もまかなう。
     */
    public static final DeferredHolder<Item, Item> CREATIVE_CELL =
            register("creative_cell", () -> new InsaneCreativeCellItem(new Item.Properties()));

    static {
        InsaneCraftingUnitType[] tiers = InsaneCraftingUnitType.values();
        for (int index = 0; index < tiers.length; index++) {
            InsaneCraftingUnitType tier = tiers[index];
            double idleDrain = idleDrain(tier);
            // ItemLike / Supplier は単一メソッドなので、登録順に依存しないよう遅延解決させる。
            ItemLike component = () -> ModItems.CELL_COMPONENTS.get(tier).get();
            StorageTier storageTier = new StorageTier(TIER_INDEX_OFFSET + index, tier.id(),
                    clampBytes(tier.getStorageBytes()), idleDrain, component::asItem);
            TIERS.put(tier, storageTier);

            // 表示名は階層ごとの lang キーではなく「書式キー + 階層ラベル」で作る → TieredNames。
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
                            MEStorageMenu.PORTABLE_ITEM_CELL_TYPE, 0x353535,
                            TieredNames.PORTABLE_ITEM_CELL, tier.label())));
            PORTABLE_FLUID_CELLS.put(tier, register("portable_fluid_cell_" + tier.id(),
                    () -> new InsanePortableCellItem(new Item.Properties(), storageTier,
                            tier.getStorageBytes(), totalTypes(tier), AEKeyType.fluids(),
                            MEStorageMenu.PORTABLE_FLUID_CELL_TYPE, 0xF1C5,
                            TieredNames.PORTABLE_FLUID_CELL, tier.label())));
        }
    }

    /**
     * その階層のセルが扱える型 (アイテムの種類) の数。
     *
     * <p>最下段の 63 (= AE2 の既定上限) から最上段の {@code 2^31-1} (約 21 億) まで、
     * <b>階層ごとにビット幅を均等に広げる</b> (18 階層で 6 bit → 31 bit なので約 1.5 bit/段)。</p>
     *
     * <p>AE2 の {@code BasicCellInventory} は 63 で頭打ちにするので、
     * {@code BasicCellInventoryMixin} が {@link jp.main.taikun.insaneae.cell.IHugeCellItem}
     * なセルに限ってその制限を外している。型 1 つあたり {@link #BYTES_PER_TYPE} バイトを
     * 消費するのは変わらないので、実際に使い切れるかは容量次第。</p>
     */
    public static int totalTypes(InsaneCraftingUnitType tier) {
        int last = InsaneCraftingUnitType.values().length - 1;
        int bits = BASE_TYPE_BITS + (MAX_TYPE_BITS - BASE_TYPE_BITS) * tier.ordinal() / last;
        return (int) ((1L << bits) - 1);
    }

    /** 1 階層ごとに 0.5 ずつ増えるアイドル消費。 */
    public static double idleDrain(InsaneCraftingUnitType tier) {
        return BASE_IDLE_DRAIN + 0.5 * tier.ordinal();
    }

    /**
     * {@link StorageTier} / {@code BasicStorageCell} が要求する int 版の容量。
     * 実容量は {@code IHugeCellItem} 経由で long のまま扱われるので、これは保険値。
     */
    public static int clampBytes(long totalBytes) {
        return (int) Math.min(Integer.MAX_VALUE, totalBytes);
    }

    /** 化学物質セルなど、他クラスからアイテムを追加するための入口。 */
    public static DeferredHolder<Item, Item> register(String name, Supplier<Item> factory) {
        return ITEMS.register(name, factory);
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
