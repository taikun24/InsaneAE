package jp.main.taikun.insaneae.testplots;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.StorageCell;
import appeng.server.testplots.TestPlot;
import appeng.server.testworld.PlotBuilder;
import appeng.server.testworld.PlotTestHelper;
import jp.main.taikun.insaneae.cell.storage.CellContentStore;
import jp.main.taikun.insaneae.crafting.InsaneCraftingUnitType;
import jp.main.taikun.insaneae.integration.ae2things.AE2ThingsIntegration;
import jp.main.taikun.insaneae.registries.ModCells;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;

import java.util.List;
import java.util.UUID;
//? if >=1.21 {
import appeng.api.ids.AEComponents;
import appeng.server.testplots.TestPlotClass;
import jp.main.taikun.insaneae.cell.storage.CellSummary;
import jp.main.taikun.insaneae.registries.ModDataComponents;
//?}
//? if <1.21 {
/*import jp.main.taikun.insaneae.cell.storage.CellNbt;
*///?}

/** ストレージセルの中身をワールド側 ({@link CellContentStore}) に置く仕組みの検証。 */
//? if >=1.21 {
@TestPlotClass
//?}
public final class CellStoragePlots {

    private static final AEItemKey COBBLE = AEItemKey.of(Items.COBBLESTONE);
    private static final AEItemKey DIRT = AEItemKey.of(Items.DIRT);

    private CellStoragePlots() {
    }

    /** 入れると UUID と要約だけがアイテムに残り、空にすると両方消えて保存データからも消える。 */
    @TestPlot("insaneae_cell_uuid_storage")
    public static void uuidStorage(PlotBuilder plot) {
        plot.creativeEnergyCell("0 -1 0");
        plot.test(helper -> helper.succeedIf(() -> {
            ItemStack cell = newCell();
            insert(helper, cell, COBBLE, 1000);

            //? if >=1.21 {
            UUID id = cell.get(ModDataComponents.CELL_ID.get());
            //?} else {
            /*UUID id = CellNbt.id(cell);
            *///?}
            helper.check(id != null, "中身を入れたセルに UUID が付かない");
            //? if >=1.21 {
            helper.check(!cell.has(AEComponents.STORAGE_CELL_INV), "中身がアイテム側にも書かれている");
            //?} else {
            /*helper.check(!cell.getOrCreateTag().contains(CellNbt.KEYS), "中身がアイテム側にも書かれている");
            *///?}
            CellContentStore.Entry entry = store(helper).get(id);
            //? if >=1.21 {
            helper.check(entry != null && entry.stacks().equals(List.of(new GenericStack(COBBLE, 1000))),
            //?} else {
            /*helper.check(entry != null && entry.types() == 1 && entry.count() == 1000,
            *///?}
                    "保存データの中身が違う: " + entry);
            //? if >=1.21 {
            CellSummary summary = cell.get(ModDataComponents.CELL_SUMMARY.get());
            helper.check(summary != null && summary.types() == 1 && summary.count() == 1000,
                    "要約が違う: " + summary);
            //?} else {
            /*helper.check(CellNbt.storedTypes(cell) == 1
                    && CellNbt.preview(cell).equals(List.of(new GenericStack(COBBLE, 1000))),
                    "要約が違う: " + cell.getTag());
            *///?}

            // 開き直しても保存データから読める。
            StorageCell reopened = inventory(helper, cell);
            helper.check(reopened.getAvailableStacks().get(COBBLE) == 1000, "開き直すと中身が見えない");

            long extracted = reopened.extract(COBBLE, 1000, Actionable.MODULATE, IActionSource.empty());
            helper.check(extracted == 1000, "取り出せない: " + extracted);
            //? if >=1.21 {
            helper.check(!cell.has(ModDataComponents.CELL_ID.get()), "空にしても UUID が残る");
            helper.check(!cell.has(ModDataComponents.CELL_SUMMARY.get()), "空にしても要約が残る");
            //?} else {
            /*helper.check(CellNbt.id(cell) == null, "空にしても UUID が残る");
            helper.check(cell.getTag() == null || !cell.getTag().contains(CellNbt.SUMMARY), "空にしても要約が残る");
            *///?}
            helper.check(store(helper).get(id) == null, "空にしても保存データが残る");
            //? if >=1.21 {
            helper.check(ItemStack.isSameItemSameComponents(cell, newCell()), "空のセルが新品と一致しない");
            //?} else {
            /*helper.check(ItemStack.isSameItemSameTags(cell, newCell()), "空のセルが新品と一致しない: " + cell.getTag());
            *///?}
        }));
    }

    /** 以前の版の形 (中身がアイテム側) のセルは、そのまま読めて、次の書き込みで保存データへ移る。 */
    @TestPlot("insaneae_cell_uuid_migration")
    public static void migration(PlotBuilder plot) {
        plot.creativeEnergyCell("0 -1 0");
        plot.test(helper -> helper.succeedIf(() -> {
            ItemStack cell = newCell();
            //? if >=1.21 {
            cell.set(AEComponents.STORAGE_CELL_INV, List.of(new GenericStack(COBBLE, 500)));
            //?} else {
            /*var keys = new net.minecraft.nbt.ListTag();
            keys.add(COBBLE.toTagGeneric());
            cell.getOrCreateTag().put(CellNbt.KEYS, keys);
            cell.getOrCreateTag().putLongArray(CellNbt.AMOUNTS, new long[] {500});
            cell.getOrCreateTag().putLong(CellNbt.ITEM_COUNT, 500);
            *///?}

            StorageCell inventory = inventory(helper, cell);
            helper.check(inventory.getAvailableStacks().get(COBBLE) == 500, "以前の形の中身が読めない");
            insert(helper, cell, DIRT, 7);

            //? if >=1.21 {
            UUID id = cell.get(ModDataComponents.CELL_ID.get());
            //?} else {
            /*UUID id = CellNbt.id(cell);
            *///?}
            helper.check(id != null, "書き込んでも UUID に移らない");
            //? if >=1.21 {
            helper.check(!cell.has(AEComponents.STORAGE_CELL_INV), "移した後もアイテム側に中身が残る");
            //?} else {
            /*helper.check(!cell.getOrCreateTag().contains(CellNbt.KEYS)
                    && !cell.getOrCreateTag().contains(CellNbt.AMOUNTS), "移した後もアイテム側に中身が残る");
            *///?}
            CellContentStore.Entry entry = store(helper).get(id);
            //? if >=1.21 {
            helper.check(entry != null && entry.stacks().size() == 2, "移した中身が違う: " + entry);
            //?} else {
            /*helper.check(entry != null && entry.types() == 2, "移した中身が違う: " + entry);
            *///?}
            StorageCell reopened = inventory(helper, cell);
            helper.check(reopened.getAvailableStacks().get(COBBLE) == 500
                    && reopened.getAvailableStacks().get(DIRT) == 7, "移した後に中身が欠けた");
        }));
    }

    /** 中身の入ったセルを別のセルに入れられない (中身がアイテムに無くても AE2 の判定が効く)。 */
    @TestPlot("insaneae_cell_uuid_no_nesting")
    public static void noNesting(PlotBuilder plot) {
        plot.creativeEnergyCell("0 -1 0");
        plot.test(helper -> helper.succeedIf(() -> {
            ItemStack inner = newCell();
            insert(helper, inner, COBBLE, 1);
            ItemStack outer = newCell();
            long inserted = inventory(helper, outer)
                    .insert(AEItemKey.of(inner), 1, Actionable.SIMULATE, IActionSource.empty());
            helper.check(inserted == 0, "中身入りのセルが別のセルに入った");
            long empty = inventory(helper, outer)
                    .insert(AEItemKey.of(newCell()), 1, Actionable.SIMULATE, IActionSource.empty());
            helper.check(empty == 1, "空のセルが別のセルに入らない");
        }));
    }

    /** AE2 Things 連携の DISK (相手が居なければ素通り)。 */
    @TestPlot("insaneae_ae2things_huge_disk")
    public static void hugeDisk(PlotBuilder plot) {
        plot.creativeEnergyCell("0 -1 0");
        plot.test(helper -> helper.succeedIf(() -> {
            if (ModList.get().isLoaded(AE2ThingsIntegration.MODID)) {
                AE2ThingsChecks.hugeDisk(helper);
            }
        }));
    }

    private static ItemStack newCell() {
        return new ItemStack(ModCells.ITEM_CELLS.get(InsaneCraftingUnitType.values()[0]).get());
    }

    private static StorageCell inventory(PlotTestHelper helper, ItemStack cell) {
        StorageCell inventory = StorageCells.getCellInventory(cell, null);
        helper.check(inventory != null, "セルの在庫が取れない");
        return inventory;
    }

    private static void insert(PlotTestHelper helper, ItemStack cell, AEItemKey what, long amount) {
        long inserted = inventory(helper, cell).insert(what, amount, Actionable.MODULATE, IActionSource.empty());
        helper.check(inserted == amount, "入らない: " + inserted);
    }

    private static CellContentStore store(PlotTestHelper helper) {
        CellContentStore store = CellContentStore.get();
        helper.check(store != null, "サーバー側で保存データが取れない");
        return store;
    }
}
