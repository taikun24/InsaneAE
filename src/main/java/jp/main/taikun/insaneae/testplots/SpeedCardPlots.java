package jp.main.taikun.insaneae.testplots;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.misc.InscriberBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.server.testplots.TestPlot;
import appeng.server.testplots.TestPlotClass;
import appeng.server.testworld.PlotBuilder;
import appeng.server.testworld.PlotTestHelper;
import jp.main.taikun.insaneae.crafting.InsaneCraftingUnitType;
import jp.main.taikun.insaneae.registries.ModCells;
import jp.main.taikun.insaneae.registries.ModUpgrades;
import jp.main.taikun.insaneae.upgrade.InsaneSpeedCardType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 加速カードの倍率が機械に効いているか。
 *
 * <p>走らせ方は {@link TestPlotSupport} を参照。</p>
 */
@TestPlotClass
public final class SpeedCardPlots {

    private SpeedCardPlots() {
    }

    /**
     * インポートバス + 限界突破加速カードで「1 tick 1 スタック」の壁が無いことの検証。
     *
     * <p>AE2 のインポートバスは {@code ExternalStorageFacade} 経由で隣接インベントリの
     * <b>全スロットを long 量でまとめて</b>抜くので、パイプの押し込みと違い
     * スタックサイズが速度の天井にならない。1 活性化あたりの移動量は
     * {@code getOperationsPerTick} で、そこにうちの加速カードの倍率が掛かる
     * ({@code IOBusPartMixin})。WARP カード 1 枚 (4096 倍) で 20 スタックが
     * まとめて動くことを見る (素のバスは 1 活性化 1 個なので、カード無しでは
     * この時間内に数個しか動かない)。</p>
     */
    @TestPlot("insaneae_import_bus_speed_card")
    public static void importBusSpeedCard(PlotBuilder plot) {
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("0 0 0");
        plot.blockEntity("1 0 0", AEBlocks.DRIVE, drive -> drive.getInternalInventory().addItems(
                new ItemStack(ModCells.ITEM_CELLS.get(InsaneCraftingUnitType.STORAGE_1G).get())));
        plot.part("0 0 0", Direction.UP, AEParts.IMPORT_BUS,
                bus -> bus.getUpgrades().addItems(new ItemStack(
                        ModUpgrades.SPEED_CARDS.get(InsaneSpeedCardType.WARP).get())));

        final int stacks = 20;
        final long total = stacks * 64L;
        ItemStack[] chestContents = new ItemStack[stacks];
        for (int i = 0; i < stacks; i++) {
            chestContents[i] = new ItemStack(Items.IRON_INGOT, 64);
        }
        plot.chest("0 1 0", chestContents);

        plot.test(helper -> {
            var pos = new BlockPos(0, 0, 0);
            var sequence = helper.startSequence();

            // グリッドの起動 + バスの活性化 (最短 5 tick 間隔) を 2〜3 回ぶん待つ。
            sequence.thenIdle(30);
            sequence.thenExecute(() -> {
                long inNetwork = countInNetwork(helper, Items.IRON_INGOT);
                helper.check(inNetwork == total,
                        stacks + " スタックがまとめて動いていない: " + inNetwork + " / " + total
                                + " (加速カードの倍率がインポートバスに効いていない)", pos);
            });

            sequence.thenSucceed();
        });
    }

    /** 刻印機の内部インベントリ (上 / 下 / 横の入力 / 横の出力 をつないだもの) の枠番号。 */
    private static final int INSCRIBER_TOP = 0;
    private static final int INSCRIBER_INPUT = 2;
    private static final int INSCRIBER_OUTPUT = 3;

    /**
     * <b>刻印機の強制クールダウンを貫通する</b>ことを確かめる。
     *
     * <p>AE2 の刻印機は加工そのものが 1 tick で終わっても、そのあとプレス動作の 16 tick が
     * 必ず挟まる。つまり加速カードで速度値をいくら上げても<b>16 tick に 1 個</b>が上限で、
     * シリコン 64 個を刻むのに 1000 tick 以上かかる。{@code TickBoost} の追い tick が
     * 効いていれば、同じ 1 tick のうちにその待ち時間ごと回してしまえる。</p>
     *
     * <p>40 tick (グリッドの起動を含む) で 64 個すべて刻めていれば貫通できている。</p>
     */
    @TestPlot("insaneae_inscriber_tick_burst")
    public static void inscriberTickBurst(PlotBuilder plot) {
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("0 0 0");
        plot.blockEntity("1 0 0", AEBlocks.INSCRIBER, inscriber -> {
            inscriber.getUpgrades().addItems(new ItemStack(
                    ModUpgrades.SPEED_CARDS.get(InsaneSpeedCardType.WARP).get()));
            var inv = inscriber.getInternalInventory();
            inv.setItemDirect(INSCRIBER_TOP, AEItems.SILICON_PRESS.stack());
            inv.setItemDirect(INSCRIBER_INPUT, AEItems.SILICON.stack(64));
        });

        plot.test(helper -> {
            var pos = new BlockPos(1, 0, 0);
            var sequence = helper.startSequence();

            sequence.thenIdle(40);
            sequence.thenExecute(() -> {
                var inv = ((InscriberBlockEntity) helper.getBlockEntity(pos)).getInternalInventory();
                long made = inv.getStackInSlot(INSCRIBER_OUTPUT).getCount()
                        + countInNetwork(helper, AEItems.SILICON_PRINT.asItem());
                helper.check(inv.getStackInSlot(INSCRIBER_INPUT).isEmpty(),
                        "シリコンが刻み切れていない: 残り "
                                + inv.getStackInSlot(INSCRIBER_INPUT).getCount()
                                + " 個 (プレス動作の 16 tick を貫通できていない)", pos);
                helper.check(made == 64,
                        "刻印済みシリコンが 64 個できていない: " + made + " 個", pos);
            });

            sequence.thenSucceed();
        });
    }

    private static long countInNetwork(PlotTestHelper helper, Item item) {
        var counter = new KeyCounter();
        helper.getGrid(BlockPos.ZERO).getStorageService().getInventory().getAvailableStacks(counter);
        return counter.get(AEItemKey.of(item));
    }
}
