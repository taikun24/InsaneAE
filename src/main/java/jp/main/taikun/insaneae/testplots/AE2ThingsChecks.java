package jp.main.taikun.insaneae.testplots;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.StorageCell;
import appeng.server.testworld.PlotTestHelper;
import io.github.projectet.ae2things.storage.DISKCellInventory;
import jp.main.taikun.insaneae.InsaneAE;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** AE2 Things のクラスに触る検査。ae2things が入っているときだけ {@link CellStoragePlots} から呼ぶ。 */
final class AE2ThingsChecks {

    private AE2ThingsChecks() {
    }

    /** 8E の DISK が AE2 Things のハンドラに拾われ、int を超える量が入り、UUID で保存される。 */
    static void hugeDisk(PlotTestHelper helper) {
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID, "disk_drive_8e"));
        helper.check(item != Items.AIR, "disk_drive_8e が登録されていない");
        ItemStack disk = new ItemStack(item);
        StorageCell inventory = StorageCells.getCellInventory(disk, null);
        helper.check(inventory instanceof DISKCellInventory, "AE2 Things のハンドラが DISK を拾わない: " + inventory);
        helper.check(((DISKCellInventory) inventory).getTotalBytes() == Long.MAX_VALUE,
                "容量が long になっていない: " + ((DISKCellInventory) inventory).getTotalBytes());

        long amount = 1L << 40;
        AEItemKey cobble = AEItemKey.of(Items.COBBLESTONE);
        long inserted = inventory.insert(cobble, amount, Actionable.MODULATE, IActionSource.empty());
        helper.check(inserted == amount, "int を超える量が入らない: " + inserted);
        helper.check(DISKCellInventory.hasDiskUUID(disk), "DISK に UUID が付かない");

        StorageCell reopened = StorageCells.getCellInventory(disk, null);
        helper.check(reopened.getAvailableStacks().get(cobble) == amount, "開き直すと中身が違う");
    }
}
