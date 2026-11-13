package jp.main.taikun.insaneae.testplots;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.StorageCell;
import appeng.server.testworld.PlotTestHelper;
import io.github.lapis256.ae2_mega_things.storage.AE2MTDISKCellInventory;
import io.github.projectet.ae2things.storage.DISKCellInventory;
import jp.main.taikun.insaneae.InsaneAE;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

/** AE2 MEGA Thingsのクラスに触る検査。ae2_mega_thingsが入っているときだけ {@link CellStoragePlots} から呼ぶ。 */
final class MegaThingsChecks {

    private MegaThingsChecks() {
    }

    /**
     * 液体DISKがMEGA Thingsのハンドラに拾われ、1000 mB = 1バイトで数えられ、intを超える量を
     * 1回で出し入れでき、16Pはlongに収まる容量で頭打ちになる。
     */
    static void fluidDisk(PlotTestHelper helper) {
        StorageCell small = inventory(helper, "fluid_disk_drive_1g");
        helper.check(small instanceof AE2MTDISKCellInventory, "MEGA Things のハンドラが拾わない: " + small);
        DISKCellInventory disk = (DISKCellInventory) small;
        helper.check(disk.getTotalBytes() == 1L << 30, "1G の容量が違う: " + disk.getTotalBytes());

        AEFluidKey water = AEFluidKey.of(Fluids.WATER);
        long amount = 1L << 35;
        long inserted = 0;
        // MEGA Things は 1 回に入れる量 (mB) を残りバイト数で切るので、何回かに分けて入る
        for (int i = 0; i < 64 && inserted < amount; i++) {
            inserted += small.insert(water, amount - inserted, Actionable.MODULATE, IActionSource.empty());
        }
        helper.check(inserted == amount, "int を超える量が入らない: " + inserted);
        helper.check(disk.getStoredItemCount() == amount / 1000, "1000 mB = 1 バイトで数えていない: " + disk.getStoredItemCount());

        long extracted = small.extract(water, amount, Actionable.MODULATE, IActionSource.empty());
        helper.check(extracted == amount, "int を超える量を 1 回で取り出せない: " + extracted);

        DISKCellInventory top = (DISKCellInventory) inventory(helper, "fluid_disk_drive_16p");
        helper.check(top.getTotalBytes() == (Long.MAX_VALUE - 999) / 1000,
                "16P が mB の long に収まる容量で止まっていない: " + top.getTotalBytes());
        Item none = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID, "fluid_disk_drive_64p"));
        helper.check(none == Items.AIR, "16P より上の液体 DISK が登録されている");
    }

    /** 化学物質DISKはappmekも居るときだけ登録される。 */
    static void chemicalDisk(PlotTestHelper helper, boolean appmekLoaded) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID, "chemical_disk_drive_1g"));
        if (!appmekLoaded) {
            helper.check(item == Items.AIR, "appmek が無いのに化学物質 DISK が登録されている");
            return;
        }
        StorageCell inventory = inventory(helper, "chemical_disk_drive_1g");
        helper.check(inventory instanceof AE2MTDISKCellInventory, "化学物質 DISK を MEGA Things のハンドラが拾わない: " + inventory);
        long rejected = inventory.insert(AEFluidKey.of(Fluids.WATER), 1000, Actionable.SIMULATE, IActionSource.empty());
        helper.check(rejected == 0, "化学物質 DISK に液体が入った");
    }

    private static StorageCell inventory(PlotTestHelper helper, String id) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID, id));
        helper.check(item != Items.AIR, id + " が登録されていない");
        StorageCell inventory = StorageCells.getCellInventory(new ItemStack(item), null);
        helper.check(inventory != null, id + " の在庫が取れない");
        return inventory;
    }
}
