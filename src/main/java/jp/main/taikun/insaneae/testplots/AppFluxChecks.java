package jp.main.taikun.insaneae.testplots;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.StorageCell;
import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import appeng.server.testworld.PlotTestHelper;
import com.glodblock.github.appflux.common.AFItemAndBlock;
import com.glodblock.github.appflux.common.me.cell.FluxCellInventory;
import com.glodblock.github.appflux.common.me.key.FluxKey;
import com.glodblock.github.appflux.common.me.key.type.EnergyType;
import com.glodblock.github.appflux.common.me.key.type.FluxKeyType;
import jp.main.taikun.insaneae.InsaneAE;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * AppliedFlux の FE セルの検査本体。AppliedFlux のクラスを直接使うので、
 * appflux が入っているときにだけ {@link AddonPlots} から呼ばれる。
 */
final class AppFluxChecks {

    private AppFluxChecks() {
    }

    static void run(PlotTestHelper helper) {
        FluxKey fe = FluxKey.of(EnergyType.FE);
        long perByte = FluxKeyType.TYPE.getAmountPerByte();

        // 1G: 階層どおりの容量 (2^30 バイト × 1 バイトあたりの FE) まで入る。
        StorageCell small = cellInventory(helper, "fe_storage_cell_1g");
        long smallCapacity = (1L << 30) * perByte;
        long accepted = small.insert(fe, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
        helper.check(accepted == smallCapacity,
                "1G の FE セルの容量が " + accepted + " FE (期待値 " + smallCapacity + ")");

        // 16T: 最上段。容量は 8T (= 2^43 バイト) だが、1.20.1 版の既定 2^22 FE/バイトでは
        // 2T で long に届くので、そこで頭打ちになる。
        // 満杯まで入れたあと出し入れが壊れないこと。
        StorageCell top = cellInventory(helper, "fe_storage_cell_16t");
        long filled = top.insert(fe, Long.MAX_VALUE, Actionable.MODULATE, IActionSource.empty());
        helper.check(filled > smallCapacity, "16T の FE セルに " + filled + " FE しか入らない (long の溢れ?)");
        helper.check(top.getStatus() == CellState.FULL, "16T の FE セルを満杯にしても FULL にならない");
        helper.check(top.insert(fe, 1, Actionable.SIMULATE, IActionSource.empty()) == 0,
                "満杯の 16T の FE セルにまだ入る");
        if (top instanceof FluxCellInventory flux) {
            long expected = Math.min(1L << 43, (Long.MAX_VALUE - perByte) / perByte);
            helper.check(flux.getTotalBytes() == expected,
                    "16T の FE セルの容量が " + flux.getTotalBytes() + " バイト (期待値 " + expected + ")");
            helper.check(flux.getUsedBytes() > 0 && flux.getUsedBytes() <= flux.getTotalBytes(),
                    "16T の FE セルの使用バイトがおかしい: " + flux.getUsedBytes() + " / " + flux.getTotalBytes());
        }
        long taken = top.extract(fe, filled, Actionable.MODULATE, IActionSource.empty());
        helper.check(taken == filled, "16T の FE セルから " + taken + " FE しか出ない (入れたのは " + filled + ")");
        helper.check(top.getStatus() == CellState.EMPTY, "全部出しても空にならない");

        // 16T より上は出さない (AppliedFlux の容量計算が long なので)。
        helper.check(!ForgeRegistries.ITEMS.containsKey(
                        ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID, "fe_storage_cell_64t")),
                "16T より上の FE セルが登録されている");

        // FE セルの芯のエネルギーコンポーネントも同じ階層ぶん。
        item(helper, "energy_component_1g");
        item(helper, "energy_component_16t");
        helper.check(!ForgeRegistries.ITEMS.containsKey(
                        ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID, "energy_component_64t")),
                "16T より上のエネルギーコンポーネントが登録されている");

        // カード: AppliedFlux が自分の FE セルにしているのと同じ顔ぶれ。
        Item cell = item(helper, "fe_storage_cell_1g");
        Item portable = item(helper, "portable_fe_cell_1g");
        helper.check(Upgrades.getMaxInstallable(AEItems.VOID_CARD, cell) > 0,
                "FE セルに超過破棄カードを登録していない");
        helper.check(Upgrades.getMaxInstallable(AFItemAndBlock.INDUCTION_CARD, portable) > 0,
                "ポータブル FE セルに誘導カードを登録していない");
        helper.check(Upgrades.getMaxInstallable(AEItems.ENERGY_CARD, portable) > 0,
                "ポータブル FE セルにエネルギーカードを登録していない");

        // FE の capability (他 Mod の機械から FE セルとして見える)。
        helper.check(new ItemStack(cell).getCapability(ForgeCapabilities.ENERGY).isPresent(),
                "FE セルに FE の capability が無い");
        helper.check(new ItemStack(portable).getCapability(ForgeCapabilities.ENERGY).isPresent(),
                "ポータブル FE セルに FE の capability が無い");
    }

    private static Item item(PlotTestHelper helper, String id) {
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID, id));
        helper.check(item != null && item != Items.AIR, id + " が登録されていない");
        return item;
    }

    private static StorageCell cellInventory(PlotTestHelper helper, String id) {
        StorageCell inventory = StorageCells.getCellInventory(new ItemStack(item(helper, id)), null);
        helper.check(inventory != null, id + " を AppliedFlux のセルハンドラが拾わない");
        return inventory;
    }
}
