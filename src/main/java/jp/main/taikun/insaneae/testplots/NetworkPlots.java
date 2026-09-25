package jp.main.taikun.insaneae.testplots;

import static jp.main.taikun.insaneae.testplots.TestPlotSupport.processingPattern;

import appeng.api.AECapabilities;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.pathing.ChannelMode;
import appeng.api.parts.BusSupport;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.upgrades.Upgrades;
import appeng.api.util.AECableType;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.core.definitions.ItemDefinition;
import appeng.parts.storagebus.StorageBusPart;
import appeng.server.testplots.CraftingPatternHelper;
import appeng.server.testplots.TestPlot;
import appeng.server.testplots.TestPlotClass;
import appeng.server.testworld.PlotBuilder;
import appeng.server.testworld.PlotTestHelper;
import jp.main.taikun.insaneae.crafting.InsaneCraftingUnitType;
import jp.main.taikun.insaneae.iface.InsaneInterfaceBlockEntity;
import jp.main.taikun.insaneae.iface.InsaneInterfacePart;
import jp.main.taikun.insaneae.network.CompressedCablePart;
import jp.main.taikun.insaneae.network.HyperCablePart;
import jp.main.taikun.insaneae.network.HyperNetwork;
import jp.main.taikun.insaneae.provider.InsanePatternProviderBlockEntity;
import jp.main.taikun.insaneae.provider.InsanePatternProviderLogic;
import jp.main.taikun.insaneae.provider.InsanePatternProviderPart;
import jp.main.taikun.insaneae.quantum.QuantumCpuBlockEntity;
import jp.main.taikun.insaneae.registries.ModBlocks;
import jp.main.taikun.insaneae.registries.ModCells;
import jp.main.taikun.insaneae.registries.ModParts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * ケーブル・部品・インターフェイス・チャンネルなど、グリッドまわりの検証。
 *
 * <p>走らせ方は {@link TestPlotSupport} を参照。</p>
 */
@TestPlotClass
public final class NetworkPlots {

    private NetworkPlots() {
    }

    /**
     * 自作の ME ストレージセルに<b>アップグレードカードが挿せる</b>ことを確かめる。
     *
     * <p>どのカードを挿せるかは {@code Upgrades.add} での登録がすべてで、
     * 登録が無いとセルワークベンチが何も受け付けない
     * (「追加されたセルに拡張カードを挿せない」不具合の回帰テスト)。
     * ワールドは使わないが、他のプロットと同じ場所で走らせる。</p>
     */
    @TestPlot("insaneae_cell_upgrades")
    public static void cellUpgrades(PlotBuilder plot) {
        plot.creativeEnergyCell("0 -1 0");
        plot.test(helper -> helper.succeedIf(() -> {
            var itemCell = ModCells.ITEM_CELLS.get(InsaneCraftingUnitType.STORAGE_1G).get();
            var fluidCell = ModCells.FLUID_CELLS.get(InsaneCraftingUnitType.STORAGE_1G).get();
            var portable = ModCells.PORTABLE_ITEM_CELLS.get(InsaneCraftingUnitType.STORAGE_1G).get();

            helper.check(Upgrades.getMaxInstallable(
                            AEItems.FUZZY_CARD, itemCell) > 0,
                    "アイテムセルにあいまいカードを登録していない");
            helper.check(Upgrades.getMaxInstallable(
                            AEItems.VOID_CARD, itemCell) > 0,
                    "アイテムセルに超過破棄カードを登録していない");
            helper.check(Upgrades.getMaxInstallable(
                            AEItems.INVERTER_CARD, fluidCell) > 0,
                    "液体セルに白黒リストカードを登録していない");
            helper.check(Upgrades.getMaxInstallable(
                            AEItems.EQUAL_DISTRIBUTION_CARD, fluidCell) > 0,
                    "液体セルに均等配分カードを登録していない");
            helper.check(Upgrades.getMaxInstallable(
                            AEItems.ENERGY_CARD, portable) == 2,
                    "ポータブルセルにエネルギーカード ×2 を登録していない");
            // MEGA Cells の Greater Energy Card。MEGA が自分のポータブルセルにしているのと同じ ×2。
            helper.check(Upgrades.getMaxInstallable(
                            gripe._90.megacells.definition.MEGAItems.GREATER_ENERGY_CARD, portable) == 2,
                    "ポータブルセルに Greater Energy Card ×2 を登録していない");
            var portableFluid = ModCells.PORTABLE_FLUID_CELLS.get(InsaneCraftingUnitType.STORAGE_1G).get();
            helper.check(Upgrades.getMaxInstallable(
                            gripe._90.megacells.definition.MEGAItems.GREATER_ENERGY_CARD, portableFluid) == 2,
                    "ポータブル液体セルに Greater Energy Card ×2 を登録していない");
        }));
    }

    /**
     * <b>パターンの受け入れルール</b>を確かめる。
     *
     * <ol>
     *   <li>特大パターンプロバイダーは Quantum CPU と同じ 1620 枠あること。</li>
     *   <li>特大パターンプロバイダーは加工・クラフト両方のパターンを受けること。</li>
     *   <li>Quantum CPU は<b>加工パターンを受け付けない</b>こと
     *       ({@code QuantumCpuLogic} のフィルタ。クラフトパターンは従来どおり受ける)。</li>
     * </ol>
     */
    @TestPlot("insaneae_pattern_acceptance")
    public static void patternAcceptance(PlotBuilder plot) {
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("[0,2] 0 0");
        plot.blockState("1 0 0", ModBlocks.QUANTUM_CPU.get().defaultBlockState());
        plot.blockState("2 0 0", ModBlocks.INSANE_PATTERN_PROVIDER.get().defaultBlockState());

        plot.test(helper -> {
            var sequence = helper.startSequence();

            sequence.thenExecute(() -> {
                var level = helper.getLevel();
                ItemStack processing = processingPattern(Items.IRON_INGOT, 2, Items.COPPER_INGOT, 1);
                // 原木 → 板材 (shapeless)。Quantum CPU が自分で組めるパターンの代表。
                ItemStack crafting = CraftingPatternHelper.encodeShapelessCraftingRecipe(level,
                        new ItemStack(Items.OAK_LOG));

                var provider = (InsanePatternProviderBlockEntity) helper.getBlockEntity(new BlockPos(2, 0, 0));
                var providerPatterns = provider.getLogic().getPatternInv();
                helper.check(providerPatterns.size() == QuantumCpuBlockEntity.PATTERN_SLOTS,
                        "特大パターンプロバイダーの枠数が " + QuantumCpuBlockEntity.PATTERN_SLOTS
                                + " ではない: " + providerPatterns.size());
                helper.check(providerPatterns.addItems(processing.copy()).isEmpty(),
                        "特大パターンプロバイダーが加工パターンを受け付けない");
                helper.check(providerPatterns.addItems(crafting.copy()).isEmpty(),
                        "特大パターンプロバイダーがクラフトパターンを受け付けない");

                var cpu = (QuantumCpuBlockEntity) helper.getBlockEntity(new BlockPos(1, 0, 0));
                var cpuPatterns = cpu.getLogic().getPatternInv();
                helper.check(!cpuPatterns.addItems(processing.copy()).isEmpty(),
                        "Quantum CPU が加工パターンを受け付けてしまった");
                helper.check(cpuPatterns.addItems(crafting.copy()).isEmpty(),
                        "Quantum CPU がクラフトパターンまで弾いている");
            });

            sequence.thenSucceed();
        });
    }

    /**
     * 自前のブロックが<b>ME ネットワークに繋がる</b>ことを確かめる。
     *
     * <p>NeoForge の capability は BlockEntityType ごとの登録制で、AE2 のクラス
     * ({@code CraftingBlockEntity} など) をそのまま使っていても
     * <b>型が自前なら AE2 の一括登録には入らない</b>。
     * {@code AECapabilities.IN_WORLD_GRID_NODE_HOST} を登録し忘れると
     * {@code GridHelper.getNodeHost} ({@code Level#getCapability} 一発) がノードを見つけられず、
     * 周りから見てただの石ころになる。{@code ModCapabilities} が抜けたときの回帰テスト。</p>
     */
    @TestPlot("insaneae_grid_connection")
    public static void gridConnection(PlotBuilder plot) {
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("[0,6] 0 0");
        // ケーブルの上に自前のブロックを 1 つずつ載せる (階層があるものは最下段で代表させる)。
        plot.blockState(new BlockPos(0, 1, 0), ModBlocks.allCraftingBlocks().get(0).defaultBlockState());
        plot.blockState(new BlockPos(1, 1, 0), ModBlocks.QUANTUM_CPU.get().defaultBlockState());
        plot.blockState(new BlockPos(2, 1, 0), ModBlocks.allEnergyCells().get(0).defaultBlockState());
        plot.blockState(new BlockPos(3, 1, 0), ModBlocks.allSolarPanels().get(0).defaultBlockState());
        plot.blockState(new BlockPos(4, 1, 0), ModBlocks.IMPROVED_CHARGER.get().defaultBlockState());
        plot.blockState(new BlockPos(5, 1, 0), ModBlocks.INSANE_INTERFACE.get().defaultBlockState());
        plot.blockState(new BlockPos(6, 1, 0), ModBlocks.INSANE_PATTERN_PROVIDER.get().defaultBlockState());

        plot.test(helper -> {
            var sequence = helper.startSequence();

            // グリッドが組み上がるまで少し待つ。
            sequence.thenIdle(5);
            sequence.thenExecute(() -> {
                IGrid grid = helper.getGrid(new BlockPos(0, 0, 0));
                for (int x = 0; x <= 6; x++) {
                    checkOnGrid(helper, new BlockPos(x, 1, 0), grid);
                }
            });

            sequence.thenSucceed();
        });
    }

    /**
     * 超特大インターフェイスの<b>枠数・1 枠の上限・long のまとめ受け</b>を確かめる。
     *
     * <p>見ているのは 3 点。</p>
     * <ol>
     *   <li>{@code GENERIC_INTERNAL_INV} が出ていて 81 枠あること
     *       (出ていないと外の機械から中身が見えない)。</li>
     *   <li>1 枠の上限が {@code Integer.MAX_VALUE} = 21 億であること。
     *       容量を上げるだけでは<b>アイテムはスタック数 (64) で頭打ちになる</b>ので、
     *       {@code ConfigInventoryAccessor} (allowOverstacking) が効いているかの回帰テストでもある。</li>
     *   <li>1 枠に入り切らない量を 1 回で入れても<b>取りこぼさない</b>こと。
     *       Mekanism 系の化学物質は long で来るので、ここで頭打ちにすると
     *       溢れたぶんが機械側に押し戻される → {@code InterfaceOverflowInventory}。</li>
     * </ol>
     */
    @TestPlot("insaneae_insane_interface")
    public static void insaneInterface(PlotBuilder plot) {
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("[0,2] 0 0");
        // 21 億を超える量を受け止められる在庫が要るので、自前の 1G セルを 1 枚積む。
        plot.blockEntity("1 0 0", AEBlocks.DRIVE, drive -> drive.getInternalInventory().addItems(
                new ItemStack(ModCells.ITEM_CELLS.get(InsaneCraftingUnitType.STORAGE_1G).get())));
        plot.blockState("2 0 0", ModBlocks.INSANE_INTERFACE.get().defaultBlockState());

        // int に収まらない量を 1 回で流し込む。
        final long inserted = 3_000_000_000L;

        plot.test(helper -> {
            var pos = new BlockPos(2, 0, 0);
            var state = new Object() {
                long accepted;
            };
            var sequence = helper.startSequence();

            // グリッドの起動 (チャネル割り当てまで) を待つ。
            sequence.thenIdle(10);

            sequence.thenExecute(() -> {
                var inv = helper.getCapability(pos, AECapabilities.GENERIC_INTERNAL_INV, null);
                helper.check(inv != null,
                        "超特大インターフェイスが GENERIC_INTERNAL_INV を公開していない", pos);
                helper.check(inv.size() == InsaneInterfaceBlockEntity.SLOTS,
                        "枠数が " + InsaneInterfaceBlockEntity.SLOTS + " ではない: " + inv.size(), pos);
                helper.check(inv.getCapacity(AEKeyType.items()) == InsaneInterfaceBlockEntity.MAX_PER_SLOT,
                        "1 枠の容量が違う: " + inv.getCapacity(AEKeyType.items()), pos);
                helper.check(inv.getMaxAmount(AEItemKey.of(Items.IRON_INGOT))
                                == InsaneInterfaceBlockEntity.MAX_PER_SLOT,
                        "1 枠に入るアイテム数がスタック数で頭打ちになっている: "
                                + inv.getMaxAmount(AEItemKey.of(Items.IRON_INGOT))
                                + " (allowOverstacking が効いていない)", pos);
                helper.check(helper.getCapability(pos, AECapabilities.ME_STORAGE, null) != null,
                        "超特大インターフェイスが ME_STORAGE を公開していない", pos);
            });

            sequence.thenExecute(() -> {
                var inv = helper.getCapability(pos, AECapabilities.GENERIC_INTERNAL_INV, null);
                state.accepted = inv.insert(0, AEItemKey.of(Items.IRON_INGOT), inserted,
                        Actionable.MODULATE);
            });

            sequence.thenExecute(() -> {
                helper.check(state.accepted == inserted,
                        "1 枠の上限 (" + InsaneInterfaceBlockEntity.MAX_PER_SLOT + ") を超えるぶんが"
                                + "押し戻された: " + state.accepted + " / " + inserted, pos);

                // 未設定の枠なので、ネットワーク側に入っているはず (枠には残らない)。
                var counter = new KeyCounter();
                helper.getGrid(BlockPos.ZERO).getStorageService().getInventory()
                        .getAvailableStacks(counter);
                long inNetwork = counter.get(AEItemKey.of(Items.IRON_INGOT));
                var inv = helper.getCapability(pos, AECapabilities.GENERIC_INTERNAL_INV, null);
                long inSlot = inv.getAmount(0);
                helper.check(inNetwork + inSlot == inserted,
                        "入れた数と行き先が合わない: ネットワーク " + inNetwork + " + 枠 " + inSlot
                                + " ≠ " + inserted, pos);
                helper.check(inNetwork == inserted,
                        "未設定の枠なのにネットワークへ直接入っていない (枠に " + inSlot + " 残っている)", pos);
            });

            // 壊したときに中身がネットワークへ戻ること。
            // AEItemKey#addDrops は 1000 スタックを超えたぶんを黙って捨てるので、
            // ドロップ任せにすると 1 枠ぶんでも大半が消える。
            final long parked = 1_000_000_000L;
            sequence.thenExecute(() -> {
                var be = (InsaneInterfaceBlockEntity) helper.getBlockEntity(pos);
                be.getInterfaceLogic().getStorage().setStack(5,
                        new GenericStack(AEItemKey.of(Items.GOLD_INGOT), parked));
            });
            sequence.thenExecute(() -> helper.destroyBlock(pos));
            sequence.thenIdle(5);
            sequence.thenExecute(() -> {
                var counter = new KeyCounter();
                helper.getGrid(BlockPos.ZERO).getStorageService().getInventory()
                        .getAvailableStacks(counter);
                long gold = counter.get(AEItemKey.of(Items.GOLD_INGOT));
                helper.check(gold == parked,
                        "壊したときに中身がネットワークへ戻っていない: " + gold + " / " + parked, pos);
            });

            sequence.thenSucceed();
        });
    }

    /**
     * ケーブル版 (プレート) がブロック版と同じ中身を持ち、グリッドにも入ることの検証。
     *
     * <p>部品は AE2 の {@code InterfacePart} / {@code PatternProviderPart} を継承して
     * {@code createLogic()} だけ差し替えている。<b>差し替えが効いていないと
     * 静かに AE2 の既定 (9 枠 / 36 枠) に戻る</b>ので、枠数と 1 枠の上限をここで押さえる。</p>
     *
     * <p>あわせて「ケーブルに貼れて、チャネルが通り、電力が来ている」ことも見る。
     * 部品はブロックと違って {@code IInWorldGridNodeHost} の登録が要らない
     * (ケーブルバスがまとめて面倒を見る) が、そこを取り違えていれば
     * {@code isActive()} が落ちる。</p>
     */
    @TestPlot("insaneae_cable_parts")
    public static void cableParts(PlotBuilder plot) {
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("0 0 0");
        plot.part("0 0 0", Direction.UP, partDefinition(
                "Insane ME Interface", ModParts.INSANE_INTERFACE));
        plot.part("0 0 0", Direction.NORTH, partDefinition(
                "Insane Pattern Provider", ModParts.INSANE_PATTERN_PROVIDER));

        plot.test(helper -> {
            var pos = new BlockPos(0, 0, 0);
            var sequence = helper.startSequence();

            // グリッドの起動 (チャネル割り当てまで) を待つ。
            sequence.thenIdle(10);

            sequence.thenExecute(() -> {
                var iface = helper.<InsaneInterfacePart>getPart(pos,
                        Direction.UP, InsaneInterfacePart.class);
                helper.check(iface != null, "超特大インターフェイスのケーブル版が置けていない", pos);

                var storage = iface.getInterfaceLogic().getStorage();
                helper.check(storage.size() == InsaneInterfaceBlockEntity.SLOTS,
                        "枠数が " + InsaneInterfaceBlockEntity.SLOTS + " ではない: " + storage.size()
                                + " (createLogic() の差し替えが効いていない)", pos);
                helper.check(storage.getMaxAmount(AEItemKey.of(Items.IRON_INGOT))
                                == InsaneInterfaceBlockEntity.MAX_PER_SLOT,
                        "1 枠に入るアイテム数が違う: "
                                + storage.getMaxAmount(AEItemKey.of(Items.IRON_INGOT))
                                + " (allowOverstacking / capacity が効いていない)", pos);
                helper.check(iface.isActive(),
                        "ケーブル版インターフェイスがグリッドに入っていない", pos);
            });

            sequence.thenExecute(() -> {
                var provider = helper.<InsanePatternProviderPart>getPart(pos,
                        Direction.NORTH, InsanePatternProviderPart.class);
                helper.check(provider != null, "特大パターンプロバイダーのケーブル版が置けていない", pos);

                int slots = provider.getLogic().getPatternInv().size();
                helper.check(slots == QuantumCpuBlockEntity.PATTERN_SLOTS,
                        "パターン枠が " + QuantumCpuBlockEntity.PATTERN_SLOTS + " ではない: " + slots
                                + " (createLogic() の差し替えが効いていない)", pos);
                helper.check(provider.getLogic() instanceof InsanePatternProviderLogic,
                        "まとめ更新版の PatternProviderLogic になっていない: "
                                + provider.getLogic().getClass().getName(), pos);
                helper.check(provider.isActive(),
                        "ケーブル版パターンプロバイダーがグリッドに入っていない", pos);
            });

            sequence.thenSucceed();
        });
    }

    /**
     * {@code PlotBuilder#part} は AE2 の {@code ItemDefinition} しか受け付けないので、
     * こちらの {@code DeferredItem} を包んで渡す。表示名はテストの出力にしか出ない。
     */
    private static <I extends Item> ItemDefinition<I> partDefinition(
            String englishName, DeferredItem<I> item) {
        return new ItemDefinition<>(englishName, item);
    }

    /** その位置のブロックが capability を公開していて、かつ同じグリッドに入っていること。 */
    private static void checkOnGrid(PlotTestHelper helper, BlockPos pos, IGrid grid) {
        String name = helper.getBlockState(pos).getBlock().getName().getString();
        var host = helper.getCapability(pos, AECapabilities.IN_WORLD_GRID_NODE_HOST, null);
        helper.check(host != null,
                name + " が IN_WORLD_GRID_NODE_HOST を公開していない (ModCapabilities の登録漏れ)", pos);
        IGridNode node = helper.getGridNode(pos);
        helper.check(node != null && node.getGrid() == grid,
                name + " がケーブルと同じネットワークに入っていない", pos);
    }

    /**
     * 超次元 ME ケーブルの<b>細さ・部品の可否・チャンネル本数</b>を確かめる。
     *
     * <p>見ているのは 3 点。</p>
     * <ol>
     *   <li>接続の型が {@code SMART} = <b>細い</b>こと (高密度は太くて部品が貼れない)。</li>
     *   <li>{@code supportsBuses()} が {@code CABLE} = <b>部品が貼れる</b>こと。
     *       実際に AE2 のストレージバスを貼って、グリッドに入るところまで見る。</li>
     *   <li>上限が<b>高密度 (32) ではなく設定値 (既定 128)</b> になっていること。
     *       解錠の条件は無く、置いた時点でこの本数になる。上限は
     *       {@code GridNode#getMaxChannels()} が全部の起点なので、そこだけを見れば
     *       経路計算も表示も追従する ({@link HyperNetwork})。</li>
     * </ol>
     */
    @TestPlot("insaneae_hyper_channels")
    public static void hyperChannels(PlotBuilder plot) {
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("[0,3] 0 0", ModParts.hyperCable(AEColor.TRANSPARENT));
        // 高密度ケーブルには貼れない部品を、細い超次元ケーブルに貼る。
        plot.part("3 0 0", Direction.NORTH, AEParts.STORAGE_BUS);

        plot.test(helper -> {
            var cablePos = new BlockPos(0, 0, 0);
            var sequence = helper.startSequence();

            sequence.thenIdle(10);

            sequence.thenExecute(() -> {
                var cable = helper.getPart(cablePos, null, HyperCablePart.class);
                helper.check(cable != null, "超次元 ME ケーブルが置けていない", cablePos);
                helper.check(cable.getCableConnectionType() == AECableType.SMART,
                        "接続の型が SMART ではない: " + cable.getCableConnectionType()
                                + " (太くなって部品が貼れなくなる)", cablePos);
                helper.check(cable.supportsBuses() == BusSupport.CABLE,
                        "部品を受け付けない: " + cable.supportsBuses(), cablePos);

                var bus = helper.getPart(new BlockPos(3, 0, 0), Direction.NORTH,
                        StorageBusPart.class);
                helper.check(bus != null,
                        "ストレージバスが超次元ケーブルに貼れていない", new BlockPos(3, 0, 0));
                helper.check(bus.isActive(),
                        "ストレージバスがグリッドに入っていない", new BlockPos(3, 0, 0));

                // 色塗りで<b>普通のスマートケーブルに化けない</b>こと。
                // CablePart#changeColor は接続の型で分岐して AE2 のケーブルに差し替えるので、
                // override を外すとここが黙って壊れる。
                helper.check(cable.changeColor(AEColor.LIME, null),
                        "色塗りを受け付けない (changeColor が false を返した)", cablePos);
                var recolored = helper.getPart(cablePos, null, HyperCablePart.class);
                helper.check(recolored != null,
                        "色を塗ったら超次元ケーブルでなくなった "
                                + "(AE2 のスマートケーブルに差し替わっている)", cablePos);
                helper.check(recolored.getCableColor() == AEColor.LIME,
                        "塗った色になっていない: " + recolored.getCableColor(), cablePos);
                helper.check(recolored.getGridNode() != null
                                && recolored.getGridNode().getGridColor() == AEColor.LIME,
                        "ノードの色が塗った色になっていない (接続の互換が元の色のまま残る)", cablePos);
                // 以降の判定に影響しないよう fluix に戻す。
                recolored.changeColor(AEColor.TRANSPARENT, null);

                IGrid grid = helper.getGrid(cablePos);
                helper.check(maxChannels(helper, cablePos) > denseChannels(helper, cablePos),
                        "上限が高密度 (32) のまま: " + maxChannels(helper, cablePos)
                                + " (GridNodeChannelMixin が当たっていない可能性)", cablePos);
                int expected = HyperNetwork.channelCapacity(
                        grid.getPathingService().getChannelMode());
                helper.check(maxChannels(helper, cablePos) == expected,
                        "上限が " + expected + " ではない: " + maxChannels(helper, cablePos), cablePos);
            });

            sequence.thenSucceed();
        });
    }

    /**
     * 圧縮 ME 高密度スマートケーブルの<b>細さ・部品の可否・チャンネル本数</b>を確かめる。
     *
     * <p>超次元ケーブルとの違いは本数だけなので、ここで見るのは
     * <b>上限が高密度と同じ 32 本のままであること</b>。ここが 128 になっていたら
     * {@code GridNodeChannelMixin} が種類を見分けられていない
     * (2 本とも {@code ThinDenseCablePart} を継承しているため、
     * {@code instanceof} の相手を親クラスに広げると黙ってこうなる)。</p>
     */
    @TestPlot("insaneae_compressed_dense_cable")
    public static void compressedDenseCable(PlotBuilder plot) {
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("[0,3] 0 0", ModParts.compressedCable(AEColor.TRANSPARENT));
        // 高密度ケーブルには貼れない部品を、細い圧縮ケーブルに貼る。
        plot.part("3 0 0", Direction.NORTH, AEParts.STORAGE_BUS);

        plot.test(helper -> {
            var cablePos = new BlockPos(0, 0, 0);
            var sequence = helper.startSequence();

            sequence.thenIdle(10);

            sequence.thenExecute(() -> {
                var cable = helper.getPart(cablePos, null, CompressedCablePart.class);
                helper.check(cable != null, "圧縮ケーブルが置けていない", cablePos);
                helper.check(cable.getCableConnectionType() == AECableType.SMART,
                        "接続の型が SMART ではない: " + cable.getCableConnectionType()
                                + " (太くなって部品が貼れなくなる)", cablePos);
                helper.check(cable.supportsBuses() == BusSupport.CABLE,
                        "部品を受け付けない: " + cable.supportsBuses(), cablePos);

                var bus = helper.getPart(new BlockPos(3, 0, 0), Direction.NORTH,
                        StorageBusPart.class);
                helper.check(bus != null,
                        "ストレージバスが圧縮ケーブルに貼れていない", new BlockPos(3, 0, 0));
                helper.check(bus.isActive(),
                        "ストレージバスがグリッドに入っていない", new BlockPos(3, 0, 0));

                // 色塗りで<b>普通のスマートケーブルにも超次元ケーブルにも化けない</b>こと。
                helper.check(cable.changeColor(AEColor.LIME, null),
                        "色塗りを受け付けない (changeColor が false を返した)", cablePos);
                var recolored = helper.getPart(cablePos, null, CompressedCablePart.class);
                helper.check(recolored != null,
                        "色を塗ったら圧縮ケーブルでなくなった", cablePos);
                helper.check(recolored.getCableColor() == AEColor.LIME,
                        "塗った色になっていない: " + recolored.getCableColor(), cablePos);
                recolored.changeColor(AEColor.TRANSPARENT, null);

                helper.check(maxChannels(helper, cablePos) == denseChannels(helper, cablePos),
                        "上限が高密度 (32) ではない: " + maxChannels(helper, cablePos)
                                + " (超次元ケーブル用の上書きが圧縮ケーブルにも効いている)", cablePos);
            });

            sequence.thenSucceed();
        });
    }

    /** その位置のノードのチャンネル上限。 */
    private static int maxChannels(PlotTestHelper helper, BlockPos pos) {
        IGridNode node = helper.getGridNode(pos);
        return node == null ? -1 : node.getMaxChannels();
    }

    /** 高密度ケーブル相当の上限 (ChannelMode の倍率込み)。解錠前の期待値。 */
    private static int denseChannels(PlotTestHelper helper, BlockPos pos) {
        ChannelMode mode = helper.getGrid(pos).getPathingService().getChannelMode();
        return 32 * mode.getCableCapacityFactor();
    }
}
