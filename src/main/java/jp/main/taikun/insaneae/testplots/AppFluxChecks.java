package jp.main.taikun.insaneae.testplots;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.StorageCell;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import appeng.server.testworld.PlotTestHelper;
import com.glodblock.github.appflux.common.AFSingletons;
import com.glodblock.github.appflux.common.me.cell.FluxCellInventory;
import com.glodblock.github.appflux.common.me.key.FluxKey;
import com.glodblock.github.appflux.common.me.key.type.EnergyType;
import com.glodblock.github.appflux.common.me.key.type.FluxKeyType;
import jp.main.taikun.insaneae.InsaneAE;
import jp.main.taikun.insaneae.iface.InsaneInterfacePart;
import jp.main.taikun.insaneae.provider.InsanePatternProviderPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.Capabilities;

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

        // 16T: 最上段。容量は 8T (= 2^43 バイト) で、既定設定ならちょうど long の際。
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
        helper.check(!BuiltInRegistries.ITEM.containsKey(
                        ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID, "fe_storage_cell_64t")),
                "16T より上の FE セルが登録されている");

        // FE セルの芯のエネルギーコンポーネントも同じ階層ぶん。
        item(helper, "energy_component_1g");
        item(helper, "energy_component_16t");
        helper.check(!BuiltInRegistries.ITEM.containsKey(
                        ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID, "energy_component_64t")),
                "16T より上のエネルギーコンポーネントが登録されている");

        // カード: AppliedFlux が自分の FE セルにしているのと同じ顔ぶれ。
        Item cell = item(helper, "fe_storage_cell_1g");
        Item portable = item(helper, "portable_fe_cell_1g");
        helper.check(Upgrades.getMaxInstallable(AEItems.VOID_CARD, cell) > 0,
                "FE セルに超過破棄カードを登録していない");
        helper.check(Upgrades.getMaxInstallable(AFSingletons.INDUCTION_CARD, portable) > 0,
                "ポータブル FE セルに誘導カードを登録していない");
        helper.check(Upgrades.getMaxInstallable(AEItems.ENERGY_CARD, portable) > 0,
                "ポータブル FE セルにエネルギーカードを登録していない");

        // FE の capability (他 Mod の機械から FE セルとして見える)。
        helper.check(new ItemStack(cell).getCapability(Capabilities.EnergyStorage.ITEM) != null,
                "FE セルに FE の capability が無い");
        helper.check(new ItemStack(portable).getCapability(Capabilities.EnergyStorage.ITEM) != null,
                "ポータブル FE セルに FE の capability が無い");
    }

    /**
     * 誘導カード: 超次元版の 4 つ (インターフェイス / パターンプロバイダー × ブロック / 部品) に挿せて、
     * 挿すと FE の capability が現れて受電できること。
     *
     * <p>FE の口は AppliedFlux が {@code GENERIC_INTERNAL_INV} を包んで出すもので、
     * 「誘導カードが挿さっているか」を見てから開く ({@code AFUtil#shouldTryCast})。
     * カードの登録 ({@code UpgradeMirrors}) と部品の capability ({@code ModCapabilities#registerParts}) の
     * どちらが欠けても、ここで null のままになる。座標は {@link AddonPlots#appFluxInductionCard} を参照。</p>
     */
    static void inductionCard(PlotTestHelper helper) {
        BlockPos cable = new BlockPos(2, 0, 0);
        checkInduction(helper, "超特大インターフェイス", new BlockPos(0, 1, 0), Direction.UP,
                (IUpgradeableObject) helper.getBlockEntity(new BlockPos(0, 1, 0)));
        checkInduction(helper, "特大パターンプロバイダー", new BlockPos(1, 1, 0), Direction.UP,
                (IUpgradeableObject) (Object) helper.getBlockEntity(new BlockPos(1, 1, 0)));
        checkInduction(helper, "超特大インターフェイス (部品)", cable, Direction.UP,
                helper.getPart(cable, Direction.UP, InsaneInterfacePart.class));
        checkInduction(helper, "特大パターンプロバイダー (部品)", cable, Direction.NORTH,
                (IUpgradeableObject) (Object) helper.getPart(cable, Direction.NORTH, InsanePatternProviderPart.class));
    }

    private static void checkInduction(PlotTestHelper helper, String name, BlockPos pos, Direction side,
            IUpgradeableObject host) {
        helper.check(host != null, name + " が見つからない", pos);
        helper.check(helper.getCapability(pos, Capabilities.EnergyStorage.BLOCK, side) == null,
                name + " に誘導カード無しで FE の口が開いている", pos);

        ItemStack rest = host.getUpgrades().addItems(new ItemStack(AFSingletons.INDUCTION_CARD));
        helper.check(rest.isEmpty(), name + " に誘導カードが挿さらない", pos);

        var energy = helper.getCapability(pos, Capabilities.EnergyStorage.BLOCK, side);
        helper.check(energy != null, name + " に誘導カードを挿しても FE の口が開かない", pos);
        int received = energy.receiveEnergy(1000, true);
        helper.check(received > 0, name + " が FE を受け取らない: " + received, pos);
    }

    private static Item item(PlotTestHelper helper, String id) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID, id));
        helper.check(item != Items.AIR, id + " が登録されていない");
        return item;
    }

    private static StorageCell cellInventory(PlotTestHelper helper, String id) {
        StorageCell inventory = StorageCells.getCellInventory(new ItemStack(item(helper, id)), null);
        helper.check(inventory != null, id + " を AppliedFlux のセルハンドラが拾わない");
        return inventory;
    }
}
