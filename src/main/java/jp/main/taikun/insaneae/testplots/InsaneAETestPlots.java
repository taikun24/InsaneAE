package jp.main.taikun.insaneae.testplots;

import appeng.api.AECapabilities;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.items.storage.CreativeCellItem;
import appeng.me.helpers.MachineSource;
import appeng.server.testplots.CraftingPatternHelper;
import appeng.server.testplots.TestPlot;
import appeng.server.testplots.TestPlots;
import appeng.server.testworld.PlotBuilder;
import appeng.server.testworld.PlotTestHelper;
import jp.main.taikun.insaneae.config.InsaneAEConfig;
import jp.main.taikun.insaneae.crafting.CraftingCalculationBatch;
import jp.main.taikun.insaneae.crafting.InsaneCraftingUnitType;
import jp.main.taikun.insaneae.iface.InsaneInterfaceBlockEntity;
import jp.main.taikun.insaneae.iface.InsaneInterfacePart;
import jp.main.taikun.insaneae.integration.aco.AcoBigIntegerJobRegistry;
import jp.main.taikun.insaneae.integration.aco.AcoCalculationIntegration;
import jp.main.taikun.insaneae.integration.aco.AcoExactLimits;
import jp.main.taikun.insaneae.provider.InsanePatternProviderBlockEntity;
import jp.main.taikun.insaneae.provider.InsanePatternProviderLogic;
import jp.main.taikun.insaneae.provider.InsanePatternProviderPart;
import jp.main.taikun.insaneae.quantum.CraftingJobView;
import jp.main.taikun.insaneae.quantum.QuantumCpuBlockEntity;
import org.jetbrains.annotations.Nullable;
import jp.main.taikun.insaneae.registries.ModBlocks;
import jp.main.taikun.insaneae.registries.ModCells;
import jp.main.taikun.insaneae.registries.ModParts;
import jp.main.taikun.insaneae.registries.ModUpgrades;
import jp.main.taikun.insaneae.upgrade.InsaneSpeedCardType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * AE2 のテストプロットに相乗りする検証。{@code appeng.tests=true} のときだけ登録する。
 *
 * <p>走らせ方: {@code ./gradlew runGameTestServer} (AE2 のテストも一緒に走る)。
 * テスト名は AE2 のアダプタ経由なので {@code ae2.insaneae_crafting_batch}。</p>
 */
@appeng.server.testplots.TestPlotClass
public final class InsaneAETestPlots {

    /** ケーキ 1 個につきバケツ 3 個が行き来する = コンテナアイテム持ちのパターン。 */
    private static final long CAKES = 4096;

    private InsaneAETestPlots() {
    }

    // AE2 19.2 では TestPlots.addPlotClass() による手動登録が廃止され、
    // @TestPlotClass の付いたクラスを FML のスキャンデータから自動で拾う方式になった
    // (プロットが実際に読まれるのは appeng.tests が有効なときだけ、というのは変わらない)。

    /**
     * クラフト計算のまとめ処理が<b>AE2 本来の計算と同じ結果になる</b>ことを確かめる。
     *
     * <p>ケーキのレシピは牛乳バケツを使う = コンテナアイテム持ちなので、
     * AE2 は本来 1 クラフトずつシミュレートする ({@code CraftingTreeProcess#limitsQuantity})。
     * 同じ発注を「まとめ処理あり」「なし」で 1 回ずつ計算し、
     * 必要バイト数・消費アイテム・パターンの実行回数がすべて一致することを見る。</p>
     */
    @TestPlot("insaneae_crafting_batch")
    public static void craftingBatch(PlotBuilder plot) {
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("[0,2] 0 0");
        plot.blockEntity("1 0 0", AEBlocks.DRIVE, drive -> {
            // 材料は無限に用意する (計算だけを見たいので在庫不足を絡めない)。
            drive.getInternalInventory().addItems(CreativeCellItem.ofItems(
                    Items.MILK_BUCKET, Items.SUGAR, Items.EGG, Items.WHEAT));
        });
        plot.block("2 0 0", AEBlocks.PATTERN_PROVIDER);

        plot.test(helper -> {
            var state = new Object() {
                Future<ICraftingPlan> pending;
                ICraftingPlan withoutBatching;
                ICraftingPlan withBatching;
                long batchedBefore;
            };

            var sequence = helper.startSequence();

            sequence.thenExecute(() -> {
                var level = helper.getLevel();
                var provider = (PatternProviderBlockEntity) helper.getBlockEntity(new BlockPos(2, 0, 0));
                provider.getLogic().getPatternInv().addItems(
                        CraftingPatternHelper.encodeShapelessCraftingRecipe(level,
                                new ItemStack(Items.MILK_BUCKET), new ItemStack(Items.MILK_BUCKET),
                                new ItemStack(Items.MILK_BUCKET),
                                new ItemStack(Items.SUGAR), new ItemStack(Items.EGG), new ItemStack(Items.SUGAR),
                                new ItemStack(Items.WHEAT), new ItemStack(Items.WHEAT),
                                new ItemStack(Items.WHEAT)));
            });

            // 1) AE2 本来の計算 (まとめ処理なし)
            sequence.thenExecuteAfter(1, () -> {
                InsaneAEConfig.setBatchCraftingCalculation(false);
                state.pending = beginCalculation(helper);
            });
            sequence.thenWaitUntil(() -> state.withoutBatching = awaitPlan(state.pending));

            // 2) まとめ処理あり
            sequence.thenExecute(() -> {
                InsaneAEConfig.setBatchCraftingCalculation(true);
                state.batchedBefore = CraftingCalculationBatch.batchedCrafts;
                state.pending = beginCalculation(helper);
            });
            sequence.thenWaitUntil(() -> state.withBatching = awaitPlan(state.pending));

            sequence.thenExecute(() -> {
                helper.check(insaneBatchingRan(state.batchedBefore),
                        "まとめ処理が一度も働いていない (Mixin が適用されていない可能性)");

                var expected = state.withoutBatching;
                var actual = state.withBatching;

                helper.check(!expected.simulation(),
                        "材料が足りずシミュレーション扱いになった: テストの前提が崩れている");
                helper.check(expected.simulation() == actual.simulation(), "simulation フラグが違う");
                helper.check(expected.bytes() == actual.bytes(),
                        "必要バイト数が違う: " + expected.bytes() + " → " + actual.bytes());
                checkSameCounts(helper, "消費アイテム", expected.usedItems(), actual.usedItems());
                checkSameCounts(helper, "emit 要求", expected.emittedItems(), actual.emittedItems());
                checkSamePatternTimes(helper, expected, actual);
            });

            sequence.thenSucceed();
        });
    }

    /**
     * <b>連鎖クラフト</b>でも計算結果が変わらないことを確かめる。
     *
     * <p>「中間素材のパターンが反映されない」という報告を受けての回帰テスト。
     * ダイヤ ← 銅 ← 鉄 の 2 段構えにして、さらに<b>銅を作れるパターンを 2 つ</b>登録する
     * (2 つ目は材料が在庫に無いので必ず失敗する枝)。
     * パターンが複数あるノードは AE2 が {@code limitsQuantity()} に関係なく
     * 1 クラフトずつ回すので、まとめ処理が一番効く一方で一番危ない経路になる。</p>
     */
    @TestPlot("insaneae_crafting_batch_chain")
    public static void craftingBatchChain(PlotBuilder plot) {
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("[0,2] 0 0");
        plot.blockEntity("1 0 0", AEBlocks.DRIVE, drive -> {
            // 鉄だけ無限にある。金は無いので「金 → 銅」の枝は必ず失敗する。
            drive.getInternalInventory().addItems(CreativeCellItem.ofItems(Items.IRON_INGOT));
        });
        plot.block("2 0 0", AEBlocks.PATTERN_PROVIDER);

        plot.test(helper -> {
            var state = new Object() {
                Future<ICraftingPlan> pending;
                ICraftingPlan withoutBatching;
                ICraftingPlan withBatching;
                long batchedBefore;
            };

            var sequence = helper.startSequence();

            sequence.thenExecute(() -> {
                var provider = (PatternProviderBlockEntity) helper.getBlockEntity(new BlockPos(2, 0, 0));
                var patterns = provider.getLogic().getPatternInv();
                // 鉄 2 → 銅 1
                patterns.addItems(processingPattern(Items.IRON_INGOT, 2, Items.COPPER_INGOT, 1));
                // 金 2 → 銅 1 (材料が無いので使えない枝)
                patterns.addItems(processingPattern(Items.GOLD_INGOT, 2, Items.COPPER_INGOT, 1));
                // 銅 4 → ダイヤ 1
                patterns.addItems(processingPattern(Items.COPPER_INGOT, 4, Items.DIAMOND, 1));
            });

            sequence.thenExecuteAfter(1, () -> {
                InsaneAEConfig.setBatchCraftingCalculation(false);
                state.pending = beginCalculation(helper, AEItemKey.of(Items.DIAMOND), 512);
            });
            sequence.thenWaitUntil(() -> state.withoutBatching = awaitPlan(state.pending));

            sequence.thenExecute(() -> {
                InsaneAEConfig.setBatchCraftingCalculation(true);
                state.batchedBefore = CraftingCalculationBatch.batchedCrafts;
                state.pending = beginCalculation(helper, AEItemKey.of(Items.DIAMOND), 512);
            });
            sequence.thenWaitUntil(() -> state.withBatching = awaitPlan(state.pending));

            sequence.thenExecute(() -> {
                var expected = state.withoutBatching;
                var actual = state.withBatching;

                helper.check(insaneBatchingRan(state.batchedBefore),
                        "まとめ処理が一度も働いていない");
                helper.check(!expected.simulation(),
                        "中間素材を作れず simulation になった: テストの前提が崩れている"
                                + " missing=" + toMap(expected.missingItems())
                                + " used=" + toMap(expected.usedItems())
                                + " patterns=" + expected.patternTimes().size());
                helper.check(expected.simulation() == actual.simulation(),
                        "simulation フラグが違う (まとめ処理側で中間素材が作れなくなっている)");
                helper.check(expected.bytes() == actual.bytes(),
                        "必要バイト数が違う: " + expected.bytes() + " → " + actual.bytes());
                checkSameCounts(helper, "消費アイテム", expected.usedItems(), actual.usedItems());
                checkSameCounts(helper, "不足アイテム", expected.missingItems(), actual.missingItems());
                checkSamePatternTimes(helper, expected, actual);
            });

            sequence.thenSucceed();
        });
    }

    /**
     * <b>特大パターンプロバイダーに入れたパターン</b>が連鎖クラフトで使われることを確かめる。
     *
     * <p>「登録してあるクラフトパターンが反映されない」という報告を受けての回帰テスト
     * (元は Quantum CPU で見ていたが、加工パターンの置き場が特大パターンプロバイダーに
     * 移ったのでこちらで見る。読み直しを 1 tick 遅らせる仕組みは共通
     * {@code InsanePatternProviderLogic#updatePatterns})。
     * グリッドのクラフト索引に載り損ねていないかをここで見る。</p>
     */
    @TestPlot("insaneae_pattern_provider_patterns")
    public static void insanePatternProviderPatterns(PlotBuilder plot) {
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("[0,2] 0 0");
        plot.blockEntity("1 0 0", AEBlocks.DRIVE, drive -> {
            drive.getInternalInventory().addItems(CreativeCellItem.ofItems(Items.IRON_INGOT));
        });
        plot.blockState("2 0 0", ModBlocks.INSANE_PATTERN_PROVIDER.get().defaultBlockState());

        plot.test(helper -> {
            var state = new Object() {
                Future<ICraftingPlan> pending;
                ICraftingPlan plan;
            };

            var sequence = helper.startSequence();

            sequence.thenExecute(() -> {
                var provider = (InsanePatternProviderBlockEntity) helper.getBlockEntity(new BlockPos(2, 0, 0));
                var patterns = provider.getLogic().getPatternInv();
                patterns.addItems(processingPattern(Items.IRON_INGOT, 2, Items.COPPER_INGOT, 1));
                patterns.addItems(processingPattern(Items.COPPER_INGOT, 4, Items.DIAMOND, 1));
            });

            // パターン投入の直後に計算を始める (遅延した読み直しが間に合っているかを見たいので待たない)。
            sequence.thenExecute(() -> state.pending = beginCalculation(helper, AEItemKey.of(Items.DIAMOND), 64));
            sequence.thenWaitUntil(() -> state.plan = awaitPlan(state.pending));

            sequence.thenExecute(() -> {
                helper.check(!state.plan.simulation(),
                        "特大パターンプロバイダーのパターンが使われなかった: missing=" + toMap(state.plan.missingItems()));
                helper.check(state.plan.missingItems().isEmpty(),
                        "不足アイテムが出た: " + toMap(state.plan.missingItems()));
                helper.check(state.plan.patternTimes().size() == 2,
                        "使われたパターンが 2 種類ではない: " + state.plan.patternTimes().size());
                // ダイヤ 64 個 = 銅 256 個 = 鉄 512 個
                helper.check(toMap(state.plan.usedItems()).getOrDefault(AEItemKey.of(Items.IRON_INGOT), 0L) == 512L,
                        "鉄の消費数が合わない: " + toMap(state.plan.usedItems()));
            });

            sequence.thenSucceed();
        });
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

            helper.check(appeng.api.upgrades.Upgrades.getMaxInstallable(
                            appeng.core.definitions.AEItems.FUZZY_CARD, itemCell) > 0,
                    "アイテムセルにあいまいカードを登録していない");
            helper.check(appeng.api.upgrades.Upgrades.getMaxInstallable(
                            appeng.core.definitions.AEItems.VOID_CARD, itemCell) > 0,
                    "アイテムセルに超過破棄カードを登録していない");
            helper.check(appeng.api.upgrades.Upgrades.getMaxInstallable(
                            appeng.core.definitions.AEItems.INVERTER_CARD, fluidCell) > 0,
                    "液体セルに白黒リストカードを登録していない");
            helper.check(appeng.api.upgrades.Upgrades.getMaxInstallable(
                            appeng.core.definitions.AEItems.EQUAL_DISTRIBUTION_CARD, fluidCell) > 0,
                    "液体セルに均等配分カードを登録していない");
            helper.check(appeng.api.upgrades.Upgrades.getMaxInstallable(
                            appeng.core.definitions.AEItems.ENERGY_CARD, portable) == 2,
                    "ポータブルセルにエネルギーカード ×2 を登録していない");
            // MEGA Cells の Greater Energy Card。MEGA が自分のポータブルセルにしているのと同じ ×2。
            helper.check(appeng.api.upgrades.Upgrades.getMaxInstallable(
                            gripe._90.megacells.definition.MEGAItems.GREATER_ENERGY_CARD, portable) == 2,
                    "ポータブルセルに Greater Energy Card ×2 を登録していない");
            var portableFluid = ModCells.PORTABLE_FLUID_CELLS.get(InsaneCraftingUnitType.STORAGE_1G).get();
            helper.check(appeng.api.upgrades.Upgrades.getMaxInstallable(
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
                        appeng.api.config.Actionable.MODULATE);
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
                        new appeng.api.stacks.GenericStack(AEItemKey.of(Items.GOLD_INGOT), parked));
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
        plot.part("0 0 0", net.minecraft.core.Direction.UP, insaneae$partDefinition(
                "Insane ME Interface", ModParts.INSANE_INTERFACE));
        plot.part("0 0 0", net.minecraft.core.Direction.NORTH, insaneae$partDefinition(
                "Insane Pattern Provider", ModParts.INSANE_PATTERN_PROVIDER));

        plot.test(helper -> {
            var pos = new BlockPos(0, 0, 0);
            var sequence = helper.startSequence();

            // グリッドの起動 (チャネル割り当てまで) を待つ。
            sequence.thenIdle(10);

            sequence.thenExecute(() -> {
                var iface = helper.<InsaneInterfacePart>getPart(pos,
                        net.minecraft.core.Direction.UP, InsaneInterfacePart.class);
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
                        net.minecraft.core.Direction.NORTH, InsanePatternProviderPart.class);
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
        plot.part("0 0 0", net.minecraft.core.Direction.UP, AEParts.IMPORT_BUS,
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

    /**
     * {@code PlotBuilder#part} は AE2 の {@code ItemDefinition} しか受け付けないので、
     * こちらの {@code DeferredItem} を包んで渡す。表示名はテストの出力にしか出ない。
     */
    private static <T extends appeng.api.parts.IPart>
            appeng.core.definitions.ItemDefinition<appeng.items.parts.PartItem<T>> insaneae$partDefinition(
                    String englishName,
                    net.neoforged.neoforge.registries.DeferredItem<appeng.items.parts.PartItem<T>> item) {
        return new appeng.core.definitions.ItemDefinition<>(englishName, item);
    }

    private static long countInNetwork(PlotTestHelper helper, net.minecraft.world.item.Item item) {
        var counter = new KeyCounter();
        helper.getGrid(BlockPos.ZERO).getStorageService().getInventory().getAvailableStacks(counter);
        return counter.get(AEItemKey.of(item));
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
     * <b>AE2 を複製した他 Mod のクラフト CPU</b> でもまとめ処理が使えることを確かめる
     * (Issue #2 の回帰テスト)。
     *
     * <p>Advanced AE (1.3.6 / 1.6.12 で確認) は {@code ExecutingCraftingJob} だけでなく
     * 進捗カウンタ {@code ElapsedTimeTracker} まで<b>自前のコピー</b>で持っている。
     * 以前は「timeTracker フィールドの型が AE2 の tracker であること」を要求していたため、
     * ここで弾かれてまとめ処理が丸ごと諦めになっていた (1 クラフトずつの遅い経路に落ちる)。</p>
     *
     * <p>AAE を dev 環境に入れられないので、<b>同じフィールド構造のフェイク CPU</b>
     * ({@link FakeForeignCpuLogic}: job / inventory / tasks / waitingFor /
     * 自前型の timeTracker / markDirty()) を {@code ReflectiveCraftingJobView} に食わせて、
     * 受理される・まとめ処理が走る・カウンタも呼ばれることを見る。</p>
     */
    @TestPlot("insaneae_bulk_foreign_cpu")
    public static void bulkForeignCpu(PlotBuilder plot) {
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("[0,2] 0 0");
        plot.blockState("2 0 0", ModBlocks.QUANTUM_CPU.get().defaultBlockState());

        final int logsInStock = 5;
        final int planksPerCraft = 4;
        final long requested = 1000;

        plot.test(helper -> {
            var sequence = helper.startSequence();

            sequence.thenExecute(() -> {
                var cpu = (QuantumCpuBlockEntity) helper.getBlockEntity(new BlockPos(2, 0, 0));
                cpu.getLogic().getPatternInv().addItems(
                        CraftingPatternHelper.encodeShapelessCraftingRecipe(helper.getLevel(),
                                new ItemStack(Items.OAK_LOG)));
            });

            sequence.thenIdle(5);

            sequence.thenExecute(() -> {
                var cpu = (QuantumCpuBlockEntity) helper.getBlockEntity(new BlockPos(2, 0, 0));
                var patterns = cpu.getLogic().getAvailablePatterns();
                helper.check(patterns.size() == 1, "パターンが 1 枚になっていない");

                // 自前型カウンタが直接呼べること (AAE の addMaxItems はパッケージプライベート)
                var tracker = new ForeignTimeTracker();
                jp.main.taikun.insaneae.quantum.TimeTrackerAdapter.addMaxItems(
                        tracker, 7, AEKeyType.items());
                helper.check(tracker.max == 7,
                        "自前型カウンタへの加算が効いていない: " + tracker.max);

                // AAE と同じフィールド構造のフェイク CPU がレイアウト検査を通ること
                var logic = new FakeForeignCpuLogic();
                logic.job.tasks.put(patterns.get(0), new ForeignTaskProgress(requested));
                logic.inventory.insert(AEItemKey.of(Items.OAK_LOG), logsInStock,
                        appeng.api.config.Actionable.MODULATE);

                var view = jp.main.taikun.insaneae.quantum.ReflectiveCraftingJobView.of(logic);
                helper.check(view != null,
                        "自前カウンタ型を持つ CPU がレイアウト検査で弾かれた (Issue #2 の再発)");

                var grid = helper.getGrid(BlockPos.ZERO);
                int pushed = jp.main.taikun.insaneae.quantum.QuantumBulkCrafting.execute(
                        view, (int) requested,
                        (appeng.me.service.CraftingService) grid.getCraftingService(),
                        grid.getEnergyService(), grid.getPivot().getLevel());

                helper.check(pushed == logsInStock,
                        "まとめ処理が期待回数走らない: " + pushed);
                long planks = logic.job.waitingFor.list.get(AEItemKey.of(Items.OAK_PLANKS));
                helper.check(planks == (long) logsInStock * planksPerCraft,
                        "完成待ちの数が合わない: " + planks);
                helper.check(logic.dirty, "markDirty が呼ばれていない");
            });

            sequence.thenSucceed();
        });
    }

    /** AAE の自前 ElapsedTimeTracker に相当。addMaxItems はパッケージプライベート (本物と同じ)。 */
    private static final class ForeignTimeTracker {
        long max;

        void addMaxItems(long amount, AEKeyType type) {
            max += amount;
        }
    }

    /** AE2 の TaskProgress に相当 (long の value フィールドだけが要る)。 */
    private static final class ForeignTaskProgress {
        long value;

        ForeignTaskProgress(long value) {
            this.value = value;
        }
    }

    /** AAE の ExecutingCraftingJob に相当するフィールド構造。 */
    private static final class ForeignExecutingJob {
        final Map<appeng.api.crafting.IPatternDetails, ForeignTaskProgress> tasks = new HashMap<>();
        final appeng.crafting.inv.ListCraftingInventory waitingFor =
                new appeng.crafting.inv.ListCraftingInventory(what -> {
                });
        final ForeignTimeTracker timeTracker = new ForeignTimeTracker();
    }

    /** AAE の AdvCraftingCPULogic に相当するフィールド構造。 */
    private static final class FakeForeignCpuLogic {
        final ForeignExecutingJob job = new ForeignExecutingJob();
        final appeng.crafting.inv.ListCraftingInventory inventory =
                new appeng.crafting.inv.ListCraftingInventory(what -> {
                });
        boolean dirty;

        public void markDirty() {
            dirty = true;
        }
    }

    /**
     * 完成品待ち台帳の BigInteger 会計 (PR #3) の回帰テスト。
     *
     * <ol>
     *   <li>long を超える量を積んでも欠けない (クランプ・折り返しが無い)</li>
     *   <li>NBT の保存 → 読み込みで量が 1 個もずれない</li>
     *   <li>壊れたエントリ (解決できないキー・負の量・空の量) は<b>例外を投げず</b>
     *       そのエントリだけ捨てる — Mod を抜いたらチャンクが壊れる、が最悪の後退なので</li>
     *   <li>旧 (long 形式) の NBT から移行できる</li>
     *   <li>ネットワークに入り切らないぶんは serverTick 後も台帳に正確に残る</li>
     * </ol>
     */
    @TestPlot("insaneae_bigint_pending_outputs")
    public static void bigintPendingOutputs(PlotBuilder plot) {
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("0 0 0");
        plot.blockState("1 0 0", ModBlocks.QUANTUM_CPU.get().defaultBlockState());

        // Long.MAX_VALUE + 5。long のどこにも収まらない代表値。
        final java.math.BigInteger overLong =
                java.math.BigInteger.valueOf(Long.MAX_VALUE).add(java.math.BigInteger.valueOf(5));
        final AEItemKey log = AEItemKey.of(Items.OAK_LOG);

        plot.test(helper -> {
            var sequence = helper.startSequence();

            sequence.thenExecute(() -> {
                var cpu = (QuantumCpuBlockEntity) helper.getBlockEntity(new BlockPos(1, 0, 0));
                var registries = helper.getLevel().registryAccess();

                // 1) long 超の量が正確に載る
                cpu.addPendingOutput(log, overLong);
                helper.check(overLong.equals(cpu.getPendingOutputs().get(log)),
                        "long 超の量が正確に積まれていない: " + cpu.getPendingOutputs().get(log));

                // 2) NBT 往復で 1 個もずれない
                var tag = new net.minecraft.nbt.CompoundTag();
                cpu.saveAdditional(tag, registries);
                cpu.loadTag(tag, registries);
                helper.check(overLong.equals(cpu.getPendingOutputs().get(log)),
                        "NBT 往復で量がずれた: " + cpu.getPendingOutputs().get(log));

                // 3) 壊れたエントリは例外なしで捨てられ、正常なエントリは残る
                var broken = tag.copy();
                var entries = broken.getCompound("pendingOutputsBig")
                        .getList("entries", net.minecraft.nbt.Tag.TAG_COMPOUND);
                var badKey = entries.getCompound(0).copy();
                badKey.getCompound("key").putString("id", "nomod:removed_item");
                entries.add(badKey);
                var badAmount = entries.getCompound(0).copy();
                badAmount.putByteArray("amount",
                        java.math.BigInteger.valueOf(-5).toByteArray());
                entries.add(badAmount);
                cpu.loadTag(broken, registries); // ここで例外が出たらテストごと落ちる = 検出できる
                helper.check(overLong.equals(cpu.getPendingOutputs().get(log)),
                        "壊れたエントリ混在で正常なエントリまで壊れた: " + cpu.getPendingOutputs().get(log));
                // 1.21 の AE2 は解決できないキーを ae2:missing_content として保全する
                // (捨てない)。負の量のエントリだけが落ち、元の 1 + 保全された 1 = 2 になる。
                helper.check(cpu.getPendingOutputs().size() == 2,
                        "壊れたエントリの扱いが想定と違う: " + cpu.getPendingOutputs());

                // 4) 旧 long 形式から移行できる
                var legacy = new net.minecraft.nbt.CompoundTag();
                cpu.saveAdditional(legacy, registries);
                legacy.remove("pendingOutputsBig");
                var legacyList = new net.minecraft.nbt.ListTag();
                legacyList.add(appeng.api.stacks.GenericStack.writeTag(registries,
                        new appeng.api.stacks.GenericStack(log, 123_456_789L)));
                legacy.put("pendingOutputs", legacyList);
                cpu.loadTag(legacy, registries);
                helper.check(java.math.BigInteger.valueOf(123_456_789L)
                                .equals(cpu.getPendingOutputs().get(log)),
                        "旧形式の移行に失敗: " + cpu.getPendingOutputs().get(log));

                // 5) の準備: long 超の量に戻す
                cpu.loadTag(tag, registries);
            });

            // serverTick が走る (このネットワークにはストレージが無いので 1 個も入らない)
            sequence.thenIdle(2);

            sequence.thenExecute(() -> {
                var cpu = (QuantumCpuBlockEntity) helper.getBlockEntity(new BlockPos(1, 0, 0));
                var grid = helper.getGrid(BlockPos.ZERO);
                long stored = grid.getStorageService().getInventory()
                        .getAvailableStacks().get(log);
                // 入ったぶん + 台帳の残り = 元の量 (1 個も消えていない)
                var pending = cpu.getPendingOutputs().getOrDefault(log, java.math.BigInteger.ZERO);
                var total = pending.add(java.math.BigInteger.valueOf(stored));
                helper.check(overLong.equals(total),
                        "serverTick 後に量が合わない: 台帳 " + pending + " + ME " + stored);
            });

            sequence.thenSucceed();
        });
    }

    /**
     * まとめクラフトが<b>材料以上に作らない</b>ことを確かめる (増殖の回帰テスト)。
     *
     * <p>{@code QuantumBulkCrafting.extractInputs} は在庫が足りなければ<b>黙って回数を減らす</b>。
     * 以前はその縮小した回数を呼び出し側に返しておらず、要求した回数のまま組ませていたため、
     * 「丸太 5 本ぶんの材料で 256 回ぶんの板材ができる」状態になっていた。
     * 多段クラフトでは中間素材が順次でき上がる = 「残り回数 &gt;&gt; 手元の材料」が通常の進行状態なので、
     * 日常的に踏む経路だった。</p>
     *
     * <p>ジョブの窓口 ({@link CraftingJobView}) を差し替えられるようにしてあるので、
     * <b>在庫をこちらで固定して直接呼べる</b>。クラフト CPU を組んで実際にジョブを流すより
     * 決定的で速い。</p>
     */
    @TestPlot("insaneae_quantum_cpu_bulk_conservation")
    public static void quantumCpuBulkConservation(PlotBuilder plot) {
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("[0,2] 0 0");
        plot.blockState("2 0 0", ModBlocks.QUANTUM_CPU.get().defaultBlockState());

        // 丸太 1 本 → 板材 4 枚 (シェイプレス)。1 回ぶんの材料が 1 個なので数え違いが起きない。
        final int logsInStock = 5;
        final int planksPerCraft = 4;
        final long requested = 1000;

        plot.test(helper -> {
            var state = new Object() {
                FakeJobView view;
                int pushed;
            };
            var sequence = helper.startSequence();

            sequence.thenExecute(() -> {
                var cpu = (QuantumCpuBlockEntity) helper.getBlockEntity(new BlockPos(2, 0, 0));
                cpu.getLogic().getPatternInv().addItems(
                        CraftingPatternHelper.encodeShapelessCraftingRecipe(helper.getLevel(),
                                new ItemStack(Items.OAK_LOG)));
            });

            // パターンの読み直しは 1 tick 遅れ、グリッドのクラフト索引の更新にもう数 tick かかる。
            sequence.thenIdle(5);

            sequence.thenExecute(() -> {
                var cpu = (QuantumCpuBlockEntity) helper.getBlockEntity(new BlockPos(2, 0, 0));
                var patterns = cpu.getLogic().getAvailablePatterns();
                helper.check(patterns.size() == 1,
                        "Quantum CPU がパターンを 1 枚だけ持っている状態にならなかった: " + patterns.size());

                var grid = helper.getGrid(BlockPos.ZERO);
                state.view = new FakeJobView(patterns.get(0), requested);
                // <b>5 回ぶんしか入れない。</b>要求は 1000 回。
                state.view.inventory.insert(AEItemKey.of(Items.OAK_LOG), logsInStock,
                        appeng.api.config.Actionable.MODULATE);

                state.pushed = jp.main.taikun.insaneae.quantum.QuantumBulkCrafting.execute(
                        state.view, (int) requested,
                        (appeng.me.service.CraftingService) grid.getCraftingService(),
                        grid.getEnergyService(), grid.getPivot().getLevel());
            });

            sequence.thenExecute(() -> {
                helper.check(state.pushed == logsInStock,
                        "材料は " + logsInStock + " 回ぶんしか無いのに " + state.pushed + " 回ぶん作った");
                helper.check(state.view.remaining == requested - logsInStock,
                        "残り回数の引き方が合わない: " + state.view.remaining);
                helper.check(state.view.inventory.list.get(AEItemKey.of(Items.OAK_LOG)) == 0,
                        "材料が使い切られていない: "
                                + state.view.inventory.list.get(AEItemKey.of(Items.OAK_LOG)));
                long planks = state.view.waitingFor.list.get(AEItemKey.of(Items.OAK_PLANKS));
                helper.check(planks == (long) logsInStock * planksPerCraft,
                        "完成待ちの数が材料と釣り合っていない: " + planks + " 枚 (材料は "
                                + logsInStock + " 本 = " + logsInStock * planksPerCraft + " 枚ぶん)");
            });

            sequence.thenSucceed();
        });
    }

    /**
     * long あふれの門番: 材料合計が long で表現できない要求が、<b>黙って負の量を流さず</b>
     * 綺麗に失敗する (craft 可能な計画に化けない) ことを確かめる。
     *
     * <p>入れ子 8^21 = 2^63 がちょうど long を超える。AE2 の
     * {@code CraftingTreeProcess.request} は「材料数 × times」をガード無しで掛けるので、
     * まとめ計算がこの規模を現実に計算可能にした結果、そこが最初に溢れる
     * (報告: @syarukasu さん)。門番は {@code CraftBranchFailure} で枝を落とすため、
     * 計算は「失敗またはシミュレーション」で終わり、負の量が計画に載ることは無い。</p>
     *
     * <p>検証するのは<b>単一パターンの一括計算</b> (times が一度に来る) — 8^21 の入れ子が
     * 実際に踏む経路。もう一方の 1 回ずつループ側の門番 (10^18 超の直接発注が必要) は、
     * シミュレーションが AE2 素の「終わらない 1 回ずつ計算」に落ちる仕様のため
     * ここでは待てない (実行パスの保護は同じ throw で効いている)。</p>
     */
    @TestPlot("insaneae_calc_overflow_guard")
    public static void calcOverflowGuard(PlotBuilder plot) {
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("[0,2] 0 0");
        plot.blockEntity("1 0 0", AEBlocks.DRIVE, drive -> {
            drive.getInternalInventory().addItems(CreativeCellItem.ofItems(Items.OAK_LOG));
        });
        plot.block("2 0 0", AEBlocks.PATTERN_PROVIDER);

        plot.test(helper -> {
            var state = new Object() {
                Future<ICraftingPlan> pending;
            };
            var sequence = helper.startSequence();

            sequence.thenExecute(() -> {
                var provider = (PatternProviderBlockEntity) helper.getBlockEntity(new BlockPos(2, 0, 0));
                // 丸太 9 → ダイヤ 1 (加工パターン): 単一パターンの一括計算経路。
                provider.getLogic().getPatternInv().addItems(
                        processingPattern(Items.OAK_LOG, 9, Items.DIAMOND, 1));
            });

            // ダイヤ × (Long.MAX/4): 材料は × 9 なので合計が long を超える。
            sequence.thenExecuteAfter(1, () -> state.pending = beginCalculation(helper,
                    AEItemKey.of(Items.DIAMOND), Long.MAX_VALUE / 4));
            sequence.thenWaitUntil(() -> checkOverflowRejected(helper, state.pending, "ダイヤ"));

            sequence.thenSucceed();
        });
    }

    /**
     * 溢れる要求の正解は「作成不可のシミュレーション計画」ただ一つ。
     *
     * <ul>
     *   <li>craft 可能な計画 → 不合格 (負の量が載っている可能性)</li>
     *   <li>null や例外 → 不合格 (プランが null だと提出側の
     *       {@code result.simulation()} が NPE になる。実機で発生した回帰)</li>
     * </ul>
     */
    private static void checkOverflowRejected(appeng.server.testworld.PlotTestHelper helper,
            Future<ICraftingPlan> pending, String label) {
        if (!pending.isDone()) {
            throw new GameTestAssertException(label + " の計算がまだ終わっていない");
        }
        try {
            ICraftingPlan plan = pending.get();
            helper.check(plan != null,
                    label + ": 計画が null (提出画面が NPE になる)");
            helper.check(plan.simulation(),
                    label + ": 溢れる要求が craft 可能な計画になった (負の量が載っている可能性)");
        } catch (ExecutionException e) {
            throw new GameTestAssertException(label + " の計算が例外で終わった: " + e.getCause());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * タスク統合カード: まとめ 1 回がクラスタ予算を <b>1 操作</b>しか消費しないことを確かめる。
     *
     * <p>クラスタ予算 3 に対して 1000 回の要求を流す。カード無しなら 3 回で頭打ちになるところが、
     * カード有りなら 1000 回まるごと 1 tick で通り、消費した操作数は 1 と報告される
     * (回数の上限はクラスタではなく Quantum CPU 自身の予算 = 加速カード 1 枚で 65536/tick)。</p>
     */
    @TestPlot("insaneae_task_fusion_card")
    public static void taskFusionCard(PlotBuilder plot) {
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("[0,2] 0 0");
        plot.blockState("2 0 0", ModBlocks.QUANTUM_CPU.get().defaultBlockState());

        final long requested = 1000;
        final int clusterBudget = 3;
        final int planksPerCraft = 4;

        plot.test(helper -> {
            var state = new Object() {
                FakeJobView view;
                int ops;
            };
            var sequence = helper.startSequence();

            sequence.thenExecute(() -> {
                var cpu = (QuantumCpuBlockEntity) helper.getBlockEntity(new BlockPos(2, 0, 0));
                cpu.getLogic().getPatternInv().addItems(
                        CraftingPatternHelper.encodeShapelessCraftingRecipe(helper.getLevel(),
                                new ItemStack(Items.OAK_LOG)));
                cpu.getUpgrades().addItems(new ItemStack(ModUpgrades.TASK_FUSION_CARD.get()));
                cpu.getUpgrades().addItems(new ItemStack(ModUpgrades.QUANTUM_ACCELERATION_CARD.get()));
            });

            // パターンの読み直しとクラフト索引の更新待ち (bulk_conservation と同じ)。
            sequence.thenIdle(5);

            sequence.thenExecute(() -> {
                var cpu = (QuantumCpuBlockEntity) helper.getBlockEntity(new BlockPos(2, 0, 0));
                helper.check(cpu.isTaskFusionInstalled(), "タスク統合カードが認識されていない");
                var patterns = cpu.getLogic().getAvailablePatterns();
                helper.check(patterns.size() == 1,
                        "Quantum CPU がパターンを 1 枚だけ持っている状態にならなかった: " + patterns.size());

                var grid = helper.getGrid(BlockPos.ZERO);
                state.view = new FakeJobView(patterns.get(0), requested);
                state.view.inventory.insert(AEItemKey.of(Items.OAK_LOG), requested,
                        appeng.api.config.Actionable.MODULATE);

                state.ops = jp.main.taikun.insaneae.quantum.QuantumBulkCrafting.execute(
                        state.view, clusterBudget,
                        (appeng.me.service.CraftingService) grid.getCraftingService(),
                        grid.getEnergyService(), grid.getPivot().getLevel());
            });

            sequence.thenExecute(() -> {
                helper.check(state.ops == 1,
                        "まとめ 1 回が 1 操作として数えられていない: " + state.ops);
                helper.check(state.view.remaining == 0,
                        "予算 " + clusterBudget + " でも全" + requested + "回通るはずが残り "
                                + state.view.remaining);
                long planks = state.view.waitingFor.list.get(AEItemKey.of(Items.OAK_PLANKS));
                helper.check(planks == requested * planksPerCraft,
                        "完成待ちの数が要求と釣り合っていない: " + planks);
            });

            sequence.thenSucceed();
        });
    }

    /**
     * タスク統合カードが <b>BigInteger (Exact) 経路でも効く</b>ことを確かめる。
     *
     * <p>{@code insaneae_task_fusion_card} が見ているのは通常の long タスク経路だけで、
     * ACO の正確な計画を受け取ったときに走る {@code executeExact} は別のループになっている。
     * ここが統合を見ていないと、<b>922京級の注文ほどカードが効かない</b>という逆の症状になる
     * (通常経路では効いているので気付きにくい)。</p>
     */
    @TestPlot("insaneae_task_fusion_exact")
    public static void taskFusionCardExact(PlotBuilder plot) {
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("[0,2] 0 0");
        plot.blockState("2 0 0", ModBlocks.QUANTUM_CPU.get().defaultBlockState());

        final long requested = 1000;
        final int clusterBudget = 3;
        final int planksPerCraft = 4;

        plot.test(helper -> {
            var state = new Object() {
                FakeJobView view;
                int ops;
            };
            var sequence = helper.startSequence();

            sequence.thenExecute(() -> {
                var cpu = (QuantumCpuBlockEntity) helper.getBlockEntity(new BlockPos(2, 0, 0));
                cpu.getLogic().getPatternInv().addItems(
                        CraftingPatternHelper.encodeShapelessCraftingRecipe(helper.getLevel(),
                                new ItemStack(Items.OAK_LOG)));
                cpu.getUpgrades().addItems(new ItemStack(ModUpgrades.TASK_FUSION_CARD.get()));
                cpu.getUpgrades().addItems(new ItemStack(ModUpgrades.QUANTUM_ACCELERATION_CARD.get()));
            });

            sequence.thenIdle(5);

            sequence.thenExecute(() -> {
                var cpu = (QuantumCpuBlockEntity) helper.getBlockEntity(new BlockPos(2, 0, 0));
                var patterns = cpu.getLogic().getAvailablePatterns();
                helper.check(patterns.size() == 1,
                        "Quantum CPU がパターンを 1 枚だけ持っている状態にならなかった: " + patterns.size());

                var grid = helper.getGrid(BlockPos.ZERO);
                state.view = new FakeJobView(patterns.get(0), requested);
                state.view.inventory.insert(AEItemKey.of(Items.OAK_LOG), requested,
                        appeng.api.config.Actionable.MODULATE);

                // Exact 台帳は ExecutingCraftingJob をキーにするが、ここでは本物のジョブを
                // 立てずに経路だけ試したいので null をキーに使う (WeakHashMap は null を許す)。
                // このプロットしか null を使わないが、並列で走る他プロットに残さないよう最後に消す。
                AcoBigIntegerJobRegistry.install(null,
                        Map.of(patterns.get(0), java.math.BigInteger.valueOf(requested)));
                state.view.exactCursor = AcoBigIntegerJobRegistry.find(null)
                        .orElseThrow(() -> new GameTestAssertException("Exact 台帳を作れなかった"))
                        .cursor(details -> {
                        });

                state.ops = jp.main.taikun.insaneae.quantum.QuantumBulkCrafting.execute(
                        state.view, clusterBudget,
                        (appeng.me.service.CraftingService) grid.getCraftingService(),
                        grid.getEnergyService(), grid.getPivot().getLevel());
                AcoBigIntegerJobRegistry.remove(null);
            });

            sequence.thenExecute(() -> {
                helper.check(state.ops == 1,
                        "Exact 経路でまとめ 1 回が 1 操作として数えられていない: " + state.ops);
                long planks = state.view.waitingFor.list.get(AEItemKey.of(Items.OAK_PLANKS));
                helper.check(planks == requested * planksPerCraft,
                        "Exact 経路で予算 " + clusterBudget + " が回数を縛っている (完成待ち "
                                + planks + " / 期待 " + requested * planksPerCraft + ")");
            });

            sequence.thenSucceed();
        });
    }

    /**
     * ACO が居るとき、Quantum CPU が<b>正確な BigInteger 実行のターゲットとして見える</b>ことを確かめる。
     *
     * <p>ACO は「{@code ICraftingProvider} が {@code ProviderOwnedPatternBatchTarget} で、
     * 返した BlockEntity が {@code CraftingTableBatchTarget}」という形でターゲットを探す。
     * <b>どちらか片方でも欠けると候補にすら入らず、黙って別の経路に落ちる</b>ので、
     * ここで両方が生えていることを見張る。</p>
     *
     * <p>ACO が無ければ何も検査せず成功する (Mixin ごと適用されないのが正しい)。
     * ACO の型を直接書かないのは、このテストが<b>両方の環境で走る</b>ため。</p>
     */
    @TestPlot("insaneae_aco_batch_target")
    public static void acoBatchTarget(PlotBuilder plot) {
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("[0,1] 0 0");
        plot.blockState("1 0 0", ModBlocks.QUANTUM_CPU.get().defaultBlockState());

        plot.test(helper -> helper.startSequence().thenExecute(() -> {
            Class<?> targetType = optionalClass(
                    "com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchTarget");
            if (targetType == null) {
                return;
            }
            Class<?> providerType = optionalClass(
                    "com.syaru.ae2craftingoptimizer.api.batch.v2.ProviderOwnedPatternBatchTarget");
            helper.check(providerType != null,
                    "ACO は居るのに ProviderOwnedPatternBatchTarget が無い (API の形が変わった)");

            var cpu = (QuantumCpuBlockEntity) helper.getBlockEntity(new BlockPos(1, 0, 0));
            helper.check(targetType.isInstance(cpu),
                    "Quantum CPU に CraftingTableBatchTarget が生えていない");
            helper.check(providerType.isInstance(cpu.getQuantumLogic()),
                    "Quantum CPU のロジックに ProviderOwnedPatternBatchTarget が生えていない");

            // 超強化クリエイティブセルの BigInteger 在庫。読みと書きで窓口が別なので両方見る。
            var cell = new jp.main.taikun.insaneae.cell.InsaneUltraCreativeCellInventory(
                    new ItemStack(ModCells.ULTRA_CREATIVE_CELL.get()));

            // 読み: ACO 1.5.20 で入った公開契約。スナップショット側はこちらを先に見る。
            Class<?> amountProvider = optionalClass("com.syaru.ae2craftingoptimizer.api.contract"
                    + ".ExactStorageAmountProvider");
            if (amountProvider == null) {
                // 1.5.20 より古い ACO。AcoMixinPlugin がこの窓口だけ当てないので、
                // 生えていないのが正しい (内部インターフェイス側だけで動く)。
                helper.check(true, "");
            } else {
                helper.check(amountProvider.isInstance(cell),
                        "超強化クリエイティブセルに公開の ExactStorageAmountProvider が生えていない "
                                + "(AcoMixinPlugin の追加判定が効いていないかもしれない)");
            }

            // 書き: 減らす側は 1.5.22 でもまだ内部 access しか知らない。
            // 向こうの名前が変わると<b>黙って効かなくなる</b> — ここで気付けるようにする。
            Class<?> exactStorage = optionalClass("com.syaru.ae2craftingoptimizer.access"
                    + ".ExtendedAePlusBigIntegerCellInventoryAccess");
            helper.check(exactStorage != null,
                    "ACO の ExtendedAePlusBigIntegerCellInventoryAccess が無い "
                            + "(書き込み側にも公開境界が入ったなら、こちらは畳んでよい)");
            helper.check(exactStorage != null && exactStorage.isInstance(cell),
                    "超強化クリエイティブセルに BigInteger 在庫の窓口が生えていない");

            // 在庫のマップは<b>毎回まったく同じインスタンス</b>であること。
            // ACO はシミュレーションとコミットで == で突き合わせ、直接書き換えて在庫を減らす。
            // コピーを返すと取引ごと巻き戻され、クラフトが進まないまま警告だけ出続ける。
            helper.check(cell.insaneae$exactAmounts() == cell.insaneae$exactAmounts(),
                    "超強化クリエイティブセルが在庫マップのコピーを返している "
                            + "(ACO の同一性検査に落ちて取引が巻き戻される)");

            // 名乗る量が ACO の計画エンジンの天井を越えていないこと。
            // 越えると BigCountMath.requireMaximumBits が投げ、
            // <b>このセルを入れただけであらゆるクラフトが WidePlanUnavailable になる</b>。
            // 上限は api.contract.ExactCountLimits (1,048,576 bit) ではなく
            // ACOConfig.bigIntegerMaximumBits (最大 54,427 bit) なので取り違えないこと。
            int ceiling = AcoExactLimits.gameplayMaximumBits();
            int advertised = jp.main.taikun.insaneae.cell.InsaneUltraCreativeCellInventory
                    .exactAmount().bitLength();
            helper.check(advertised < ceiling,
                    "超強化クリエイティブセルが名乗る量 (" + advertised + " bit) が "
                            + "ACO の上限 (" + ceiling + " bit) を越えている");
            // 種類数を掛けた合計や複数セルの合算にも余地が要る。
            helper.check(advertised <= ceiling / 2,
                    "名乗る量 (" + advertised + " bit) に足し算の余地が無い "
                            + "(ACO の上限 " + ceiling + " bit の半分までにすること)");
        }).thenSucceed());
    }

    /**
     * ACO の BigInteger 計画が持つ<b>正確な</b>必要バイト数。
     *
     * <p>{@code plan.bytes()} は long に飽和するので、断られた理由が
     * 「BigInteger 計画が作れなかった」のか「作れたが容量が足りない」のかを
     * 区別できない。ここが {@code <BigInteger計画なし>} なら前者。</p>
     */
    private static String insaneae$exactPlanBytes(appeng.api.networking.crafting.ICraftingPlan plan) {
        try {
            return jp.main.taikun.insaneae.integration.aco.AcoBigIntegerPlanBridge.inspect(plan)
                    .map(exact -> exact.exactBytes().toString())
                    .orElse("<BigInteger計画なし>");
        } catch (RuntimeException | LinkageError unavailable) {
            return "<読めず: " + unavailable + ">";
        }
    }

    /** 居なければ null。ACO 連携のテストを ACO 無しの環境でも走らせるため。 */
    @Nullable
    private static Class<?> optionalClass(String name) {
        try {
            return Class.forName(name, false, InsaneAETestPlots.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError absent) {
            return null;
        }
    }

    /**
     * Advanced AE のクラフト CPU へ入れている compat Mixin が<b>実際に当たっている</b>ことを確かめる。
     *
     * <p>{@code @Pseudo} + {@code targets} の名指しは、相手のクラス名が変わっても
     * <b>エラーにならず黙って当たらなくなる</b> ({@code required=false} なので尚更)。
     * 症状は「Advanced AE のクラフト CPU だけ遅い」「巨大な協調処理数だと途中で止まる」で、
     * どちらもログに何も出ないため、当たっているかどうかはここで見張るしかない。</p>
     *
     * <p>Advanced AE が入っていない環境では<b>何も検査せずに成功する</b>
     * (通常のゲームテストは Advanced AE 無しで回るため)。
     * 相手のクラスは普段ロードされないので、{@code Class.forName} で明示的に読み込んで
     * Mixin の変換を走らせてから、注入したメソッドが生えているかを見る。</p>
     */
    @TestPlot("insaneae_aae_cpu_mixins")
    public static void advancedAeCpuMixins(PlotBuilder plot) {
        // 中身は反射で見るだけだが、空のプロットは AE2 の Plot#getBounds が通らないので 1 つ置く。
        plot.cable("0 0 0");
        plot.test(helper -> helper.startSequence().thenExecute(() -> {
            if (!net.neoforged.fml.ModList.get().isLoaded("advanced_ae")) {
                return;
            }
            Class<?> logic;
            try {
                logic = Class.forName("net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic");
            } catch (ClassNotFoundException missing) {
                throw new GameTestAssertException(
                        "Advanced AE は居るのに AdvCraftingCPULogic が無い。"
                                + "クラス名が変わったので compat Mixin の targets を直すこと: " + missing);
            }
            // Mixin は注入ハンドラを handler$<hash>$<元の名前> / redirect$... に改名して混ぜるので、
            // 名前の一致ではなく<b>末尾</b>で見る。@Unique のメソッドだけは元の名前のまま入る。
            Set<String> injected = new HashSet<>();
            for (var method : logic.getDeclaredMethods()) {
                if (method.getName().contains("insaneae$")) {
                    injected.add(method.getName());
                }
            }
            // まとめ処理 (AdvCraftingCpuLogicMixin) と 1 tick 予算の long 化 (AdvCraftingCpuBudgetMixin)。
            for (String expected : List.of("insaneae$bulkCrafting", "insaneae$reduceBudget",
                    "insaneae$addBulkToResult", "insaneae$tickBudget", "insaneae$rollUsedOps")) {
                helper.check(injected.stream().anyMatch(name -> name.endsWith(expected)),
                        "Advanced AE の CPU へ " + expected + " が注入されていない (当たったのは "
                                + injected + ")");
            }

            // クラスタ側の容量の飽和 (AdvCraftingCpuStorageMixin)。当たっていないと
            // InsaneAE のクラフトストレージを数個積んだだけで容量が負に折り返す。
            Class<?> cluster;
            try {
                cluster = Class.forName("net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPUCluster");
            } catch (ClassNotFoundException missing) {
                throw new GameTestAssertException(
                        "Advanced AE は居るのに AdvCraftingCPUCluster が無い。"
                                + "クラス名が変わったので compat Mixin の targets を直すこと: " + missing);
            }
            Set<String> clusterInjected = new HashSet<>();
            for (var method : cluster.getDeclaredMethods()) {
                if (method.getName().contains("insaneae$")) {
                    clusterInjected.add(method.getName());
                }
            }
            for (String expected : List.of("insaneae$saturateStorageBytes",
                    "insaneae$saturateStorageMultiplier")) {
                helper.check(clusterInjected.stream().anyMatch(name -> name.endsWith(expected)),
                        "Advanced AE のクラスタへ " + expected + " が注入されていない (当たったのは "
                                + clusterInjected + ")");
            }
        }).thenSucceed());
    }

    /**
     * <b>実際のジョブ</b>でまとめ処理が発火することを確かめる (同居 Mod に対する回帰テスト)。
     *
     * <p>他のまとめ処理テストは {@code QuantumBulkCrafting.execute} を直接呼んでいるので、
     * <b>クラフト CPU の tick から本当に呼ばれているか</b>は見ていない。
     * {@code executeCrafting} の先頭には打ち切り付きで注入している Mod が他にもいる
     * (AE2 Crafting Optimizer がそう) ため、先を越されるとまとめ処理は<b>黙って</b>
     * 素の 1 回ずつに戻る — 結果は同じで遅くなるだけなので、カウンタでしか気付けない。</p>
     *
     * <p>タスク統合カードを挿してあるので、まとめ処理が効いていれば
     * {@code crafts} 回は数 tick で終わる。効いていなければクラスタ予算 (1 tick に数回) で
     * 刻まれるため、待ち時間の側でも差が出る。</p>
     */
    /**
     * <b>クリエイティブセルから材料を供給したクラフトが、最後まで終わるか。</b>
     *
     * <p>「クラフトは進行中のままタスクが空になり、いつまでも完了しない」という症状を
     * 追うためのテスト。クリエイティブセル (強化・超強化・AE2 本家とも同じ) は</p>
     *
     * <pre>
     * insert(what, amount)        → 設定済みの種類は<b>無限に飲み込む</b> (= 実質ボイド)
     * isPreferredStorageFor(what) → 設定済みの種類は<b>優先搬入先を名乗る</b>
     * </pre>
     *
     * <p>なので、<b>クラフトの完成品がセルに設定されていると、完成品がクラフト CPU の
     * 完成待ちに返る前に吸い込まれて消える</b>おそれがある。そうなると完成待ちが永遠に
     * 減らず、タスクだけ空になってジョブが終わらない。</p>
     *
     * <p>ここでは 2 つの並びを両方とも「完了すること」で検査する:</p>
     * <ol>
     *   <li>セルには<b>材料だけ</b> — 想定どおりの使い方</li>
     *   <li>セルに<b>材料と完成品の両方</b> — 上の懸念そのままの並び</li>
     * </ol>
     *
     * <p>2 が落ちるならセルが完成品を飲んでいる。1 が落ちるなら供給側の問題。
     * どちらも通るなら、完了しない原因はここではない。</p>
     */
    @TestPlot("insaneae_craft_from_creative_cell")
    public static void craftFromCreativeCell(PlotBuilder plot) {
        insaneae$craftCompletionPlot(plot, false);
    }

    /** {@link #craftFromCreativeCell} の並び 2 — 完成品もセルに設定してある場合。 */
    @TestPlot("insaneae_craft_output_also_in_cell")
    public static void craftOutputAlsoInCell(PlotBuilder plot) {
        insaneae$craftCompletionPlot(plot, true);
    }

    /**
     * <b>Advanced AE のクラフト CPU に載せたジョブが、Quantum CPU 経由で最後まで終わるか。</b>
     *
     * <p>{@link #craftFromCreativeCell} と同じ内容を、クラフト CPU だけ
     * Advanced AE の量子コンピュータに差し替えたもの。報告されている
     * 「進行中のままタスクが空で終わらない」は<b>この並び</b>で起きている。</p>
     *
     * <p>Advanced AE が入っていない環境では<b>何も検査せずに成功する</b>
     * ({@code ./gradlew runGameTestServer -PwithAdvancedAe=true -PwithAco} で有効になる)。</p>
     */
    @TestPlot("insaneae_craft_on_advanced_ae_cpu")
    public static void craftOnAdvancedAeCpu(PlotBuilder plot) {
        var core = insaneae$aaeBlock("quantum_core");
        var unit = insaneae$aaeBlock("quantum_storage_256");
        var shell = insaneae$aaeBlock("quantum_structure");
        if (core == null || unit == null || shell == null) {
            // 空のプロットは AE2 の Plot#getBounds が通らないので 1 つ置く。
            plot.cable("0 0 0");
            plot.test(helper -> helper.startSequence().thenSucceed());
            return;
        }

        plot.creativeEnergyCell("0 -1 0");
        plot.cable("[0,4] 0 0");
        plot.blockEntity("1 0 0", AEBlocks.DRIVE, drive -> {
            drive.getInternalInventory().addItems(insaneae$ultraCreativeCell(Items.OAK_LOG));
            drive.getInternalInventory().addItems(AEItems.ITEM_CELL_64K.stack());
        });
        plot.blockState("3 0 0", ModBlocks.QUANTUM_CPU.get().defaultBlockState());

        // Advanced AE の量子コンピュータは<b>中空の箱</b>で、外殻が quantum_structure、
        // 中身が機能ブロックという構造 (AdvCraftingCPUCalculator#verifyInternalStructure)。
        // 5..7 x 0..3 x 0..2 の箱にすると、内側はちょうど (6,1,1) と (6,2,1) の 2 マス。
        plot.blockState("[5,7] [0,3] [0,2]", shell.defaultBlockState());
        plot.blockState("6 1 1", core.defaultBlockState());
        plot.blockState("6 2 1", unit.defaultBlockState());

        final long requested = 64;

        plot.test(helper -> {
            var state = new Object() {
                appeng.server.testworld.TestCraftingJob job;
            };
            var sequence = helper.startSequence();

            sequence.thenExecute(() -> {
                var cpu = (QuantumCpuBlockEntity) helper.getBlockEntity(new BlockPos(3, 0, 0));
                // 2 段のツリー: ボタン <- 板材 <- 原木。実環境の 8^N 連鎖に形を寄せてある
                // (在庫にあるのは原木だけなので、途中段も必ずクラフトされる)。
                cpu.getLogic().getPatternInv().addItems(
                        CraftingPatternHelper.encodeShapelessCraftingRecipe(helper.getLevel(),
                                new ItemStack(Items.OAK_LOG)));
                cpu.getLogic().getPatternInv().addItems(
                        CraftingPatternHelper.encodeShapelessCraftingRecipe(helper.getLevel(),
                                new ItemStack(Items.OAK_PLANKS)));
                // 実環境と同じカード構成 (加速 7 + タスク統合 1)。
                for (int i = 0; i < QuantumCpuBlockEntity.MAX_ACCELERATION_CARDS; i++) {
                    cpu.getUpgrades().addItems(
                            new ItemStack(ModUpgrades.QUANTUM_ACCELERATION_CARD.get()));
                }
                cpu.getUpgrades().addItems(new ItemStack(ModUpgrades.TASK_FUSION_CARD.get()));
            });
            // 多ブロック構造が組み上がるまで少し待つ。
            sequence.thenIdle(20);

            // 先に CPU が組めているか見る。組めていないと「failed to submit job」としか出ず、
            // 構造の問題なのか実行の問題なのか区別がつかない。
            sequence.thenExecute(() -> {
                int cpus = 0;
                for (var ignored : helper.getGrid(BlockPos.ZERO).getCraftingService().getCpus()) {
                    cpus++;
                }
                helper.check(cpus > 0, "Advanced AE の量子コンピュータが組み上がっていない "
                        + "(外殻・中身の並びか、必要ブロックが変わった可能性)");
            });

            sequence.thenExecute(() -> state.job = new appeng.server.testworld.TestCraftingJob(
                    helper, BlockPos.ZERO, AEItemKey.of(Items.OAK_BUTTON), requested));
            sequence.thenWaitUntil(() -> state.job.tickUntilStarted());

            sequence.thenWaitUntil(() -> {
                long stored = insaneae$storedAmount(helper, Items.OAK_BUTTON);
                if (stored < requested) {
                    throw new GameTestAssertException("Advanced AE の CPU で 2 段クラフトが "
                            + stored + "/" + requested + " しか進まない");
                }
            });
            sequence.thenWaitUntil(() -> {
                for (var cpu : helper.getGrid(BlockPos.ZERO).getCraftingService().getCpus()) {
                    if (cpu.isBusy()) {
                        throw new GameTestAssertException(
                                "完成品は揃ったのに Advanced AE の CPU がジョブを抱えたまま "
                                        + "(完成待ちが減っていない)");
                    }
                }
            });

            // compat Mixin が<b>実際に走った</b>こと。名前が生えているかを見る検査では
            // 足りない (Mixin はメソッドを混ぜてから injector を配線するので、
            // 配線に失敗してもメソッドだけは生える)。
            sequence.thenExecute(() -> {
                helper.check(jp.main.taikun.insaneae.compat.AaeCompatCounters
                                .storageSaturations > 0,
                        "AdvCraftingCpuStorageMixin が一度も走っていない "
                                + "(@Redirect の配線に失敗している可能性 — ログの "
                                + "InvalidInjectionException を確認すること)");
                helper.check(jp.main.taikun.insaneae.compat.AaeCompatCounters
                                .budgetCalculations > 0,
                        "AdvCraftingCpuBudgetMixin が一度も走っていない (同上)");
            });
            sequence.thenSucceed();
        });
    }

    /**
     * <b>long を超える要求が BigInteger 経路で実際に走り出すか。</b>
     *
     * <p>{@link #craftFromCreativeCell} は long に収まる規模なので、AE2 本来の経路しか
     * 通らない。報告されている症状は<b>そこを超えた規模でだけ</b>出るので、
     * こちらは要求を {@code Long.MAX_VALUE} にして ACO の exact 経路を必ず踏ませる。</p>
     *
     * <p>2 段のツリー (ボタン &lt;- 板材 &lt;- 原木) なので、パターン実行回数の合計は
     * 要求量の 1.25 倍ほどになり、<b>合計が long に収まらない</b>。
     * ACO はここで {@code hasAggregatePastLong()} を見て wide plan へ切り替える。</p>
     *
     * <h2>何を検査するか</h2>
     * <p>この規模は<b>完了しなくて当たり前</b>なので、完了は見ない。見るのは</p>
     * <ol>
     *   <li>投入が通ること (「failed to submit job」にならない)</li>
     *   <li><b>実際に完成品が増え続けること</b> — 「進行中のまま何も起きない」を捕まえる</li>
     * </ol>
     *
     * <p>クラフト CPU は InsaneAE の BigInteger クラフト CPU (理論上限容量)。
     * 普通のクラフトストレージだと、この規模は容量不足で投入前に弾かれてしまう。</p>
     *
     * <h2>2026-08-15 時点の結果と、分かったこと</h2>
     * <p><b>このテストは失敗する。</b>投入が {@code CPU_TOO_SMALL} で断られるが、
     * <b>容量の問題ではない</b> — 必要 bytes も CPU の空きも同じ {@code Long.MAX_VALUE} で、
     * AE2 自身の判定 ({@code available >= bytes}) は通っている。
     * 返しているのは ACO の {@code CraftingCpuClusterBigCapacityGuardMixin} で、
     * 「wide plan なのに BigInteger の裏付けが無い」ときに <b>CPU_TOO_SMALL を騙る</b>。</p>
     *
     * <p>裏付けが無い理由は ACO の診断が教えてくれる: <b>{@code NO_COMPILED_PROGRAM}</b>。
     * {@code CompiledRootProgram} が組めていない。
     * {@code Ae2CompiledPatternFactory} は各パターンの「完全さ」を
     * {@code IPatternDetails.supportsPushInputsToExternalInventory()} で決めており、
     * <b>クラフトテーブル用パターンはこれが false</b> (組み立てるものであって
     * 外部インベントリへ押し出すものではないため)。
     * 不完全なパターンに触れる木は {@code rootProgram} が空になる。</p>
     *
     * <p><b>この見立ては対照実験 ({@link #craftPastLongProcessing}) で否定された。</b>
     * 同じ木を加工パターンで組んでも、まったく同じ {@code NO_COMPILED_PROGRAM} で断られる。
     * パターンの種類は関係ない。</p>
     *
     * <p>さらに {@link #insaneae$acoGraphProbe} で ACO の compiled graph を直接覗くと、
     * 木は<b>完全に健全</b>だった:</p>
     *
     * <pre>
     * oak_button: patterns=1 oneFullyCompiled=true incomplete=false cyclic=false rootProgram=true
     * oak_planks: patterns=1 oneFullyCompiled=true incomplete=false cyclic=false rootProgram=true
     * </pre>
     *
     * <p>断り文句は {@code getOrCompile(grid, level).rootProgram(what).isEmpty()} が
     * 真だったという意味なのに、<b>同じ呼び出しをこちらでやると present が返る</b>。
     * つまり<b>ACO 自身の判断と ACO 自身の graph が食い違っている</b>。
     * ここから先は ACO 側の問題で、InsaneAE のパターンや在庫の出し方の話ではない。</p>
     *
     * <p><b>注意: ACO を載せるには {@code -PwithAco=true} と書くこと。</b>
     * {@code -PwithAco} だけだと値が空文字になり、build.gradle の {@code == 'true'} が
     * 偽になって<b>黙って ACO 無しで走る</b> (テストは通ってしまう)。</p>
     */
    /**
     * ACO 1.5.23 以降、exact ジョブの実行を ACO が所有していることを<b>こちらが認識できる</b>か。
     *
     * <p>{@code executeCrafting} の @HEAD にはこちらのまとめ処理と ACO の打ち切りが両方刺さって
     * いて、実機の適用順ではこちらが先に走る。ACO の所有を見落とすと、ACO の台帳に無い実行を
     * 1 回進めてしまう。ここで検査するのは<b>判定そのもの</b> — ACO が生やすメソッド名
     * ({@code aco$isExactJob}) は相手の都合で変わりうるのに、変わっても<b>エラーにならず
     * 黙って false になる</b>ため、注入の有無ではなく実ジョブに対する戻り値で見張る。</p>
     *
     * <p>ACO が古い (1.5.22 以下) 環境では共通契約のクラスが無いので何も検査しない。</p>
     */
    @TestPlot("insaneae_aco_exact_ownership")
    public static void acoExactOwnership(PlotBuilder plot) {
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("[0,3] 0 0");
        plot.blockEntity("1 0 0", AEBlocks.DRIVE, drive -> {
            drive.getInternalInventory().addItems(insaneae$ultraCreativeCell(Items.OAK_LOG));
            drive.getInternalInventory().addItems(new ItemStack(
                    ModCells.ITEM_CELLS.get(InsaneCraftingUnitType.STORAGE_8E).get()));
        });
        plot.blockState("2 [0,1] [0,2]", ModBlocks.BIG_INTEGER_CPU.get().defaultBlockState());
        plot.block("2 1 0", AEBlocks.CRAFTING_ACCELERATOR);
        plot.blockState("3 0 0", ModBlocks.QUANTUM_CPU.get().defaultBlockState());

        plot.test(helper -> {
            // 共通契約 (ACO 1.5.23 で AAE 専用から切り出されたもの) が無ければ何も検査しない。
            if (optionalClass("com.syaru.ae2craftingoptimizer.access.ExactCraftingJobAccess") == null) {
                helper.startSequence().thenSucceed();
                return;
            }
            var state = new Object() {
                appeng.me.helpers.MachineSource source;
                java.util.concurrent.Future<appeng.api.networking.crafting.ICraftingPlan> plan;
            };
            var sequence = helper.startSequence();

            sequence.thenExecute(() -> {
                var cpu = (QuantumCpuBlockEntity) helper.getBlockEntity(new BlockPos(3, 0, 0));
                cpu.getLogic().getPatternInv().addItems(
                        CraftingPatternHelper.encodeShapelessCraftingRecipe(helper.getLevel(),
                                new ItemStack(Items.OAK_LOG)));
                cpu.getLogic().getPatternInv().addItems(
                        CraftingPatternHelper.encodeShapelessCraftingRecipe(helper.getLevel(),
                                new ItemStack(Items.OAK_PLANKS)));
                for (int i = 0; i < QuantumCpuBlockEntity.MAX_ACCELERATION_CARDS; i++) {
                    cpu.getUpgrades().addItems(
                            new ItemStack(ModUpgrades.QUANTUM_ACCELERATION_CARD.get()));
                }
                cpu.getUpgrades().addItems(new ItemStack(ModUpgrades.TASK_FUSION_CARD.get()));
            });
            sequence.thenIdle(60);

            sequence.thenExecute(() -> {
                state.source = new appeng.me.helpers.MachineSource(
                        (appeng.api.networking.security.IActionHost)
                                helper.getBlockEntity(new BlockPos(3, 0, 0)));
                state.plan = helper.getGrid(BlockPos.ZERO).getCraftingService()
                        .beginCraftingCalculation(helper.getLevel(), () -> state.source,
                                AEItemKey.of(Items.OAK_BUTTON), Long.MAX_VALUE,
                                appeng.api.networking.crafting.CalculationStrategy.REPORT_MISSING_ITEMS);
            });
            sequence.thenWaitUntil(() -> helper.check(state.plan.isDone(), "計算が終わらない"));
            sequence.thenExecute(() -> {
                appeng.api.networking.crafting.ICraftingPlan plan;
                try {
                    plan = state.plan.get();
                } catch (Exception failure) {
                    throw new GameTestAssertException("計算が例外で終わった: " + failure);
                }
                helper.check(!plan.simulation(), "計算がシミュレーション止まり (素材不足扱い)");
                var result = helper.getGrid(BlockPos.ZERO).getCraftingService()
                        .submitJob(plan, null, null, false, state.source);
                helper.check(result.successful(),
                        "long を超える要求の投入が断られた: errorCode=" + result.errorCode());
            });

            // 投入直後に見る。ACO が隔離してジョブを畳んだあとでは判定できない。
            sequence.thenIdle(2);
            sequence.thenExecute(() -> {
                appeng.crafting.execution.ExecutingCraftingJob job = null;
                for (var cpu : helper.getGrid(BlockPos.ZERO).getCraftingService().getCpus()) {
                    if (cpu instanceof appeng.me.cluster.implementations.CraftingCPUCluster cluster) {
                        var found = ((jp.main.taikun.insaneae.mixin.CraftingCpuLogicJobAccessor)
                                (Object) cluster.craftingLogic).insaneae$getJob();
                        if (found != null) {
                            job = found;
                            break;
                        }
                    }
                }
                helper.check(job != null, "投入したはずのジョブが CPU に無い");
                helper.check(
                        jp.main.taikun.insaneae.integration.aco.AcoExactJobOwnership.isAcoOwned(job),
                        "ACO 1.5.23 以降なのに exact ジョブの所有を認識できていない。"
                                + "ACO 側の判定メソッド名 (aco$isExactJob) が変わった可能性がある。"
                                + " job=" + job.getClass().getName()
                                + " 観測回数=" + jp.main.taikun.insaneae.integration.aco
                                        .AcoExactJobOwnership.observedAcoOwnedJobs);
            });
            sequence.thenSucceed();
        }).maxTicks(400);
    }

    @TestPlot("insaneae_craft_past_long")
    public static void craftPastLong(PlotBuilder plot) {
        // 要求量。完了判定にも使うので 1 か所にまとめる。
        final long requested = Long.MAX_VALUE;
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("[0,3] 0 0");
        plot.blockEntity("1 0 0", AEBlocks.DRIVE, drive -> {
            drive.getInternalInventory().addItems(insaneae$ultraCreativeCell(Items.OAK_LOG));
            // 完成品の置き場。64K セルだと 1 種類で 520,192 個 ((65536-512)*8) で満杯になり、
            // クラフトが「途中で止まった」ようにしか見えない。8E セルなら 3.6e19 個入るので
            // Long.MAX_VALUE の注文でも置き場が先に尽きない。
            drive.getInternalInventory().addItems(new ItemStack(
                    ModCells.ITEM_CELLS.get(InsaneCraftingUnitType.STORAGE_8E).get()));
        });
        // 理論上限容量の BigInteger クラフト CPU。普通のクラフトストレージだと
        // この規模は「容量が足りない」で投入前に弾かれる。
        // 1 個では足りない。この注文の正確な必要量は 3.2e19 bytes で、
        // このブロック 1 個の容量 (2^63-1 = 9.2e18) の約 3.5 倍ある。
        // 足りないまま投入すると CPU_TOO_SMALL で断られ、
        // <b>ACO 側の不具合と見分けが付かない</b>ので、余裕を持って 5 個積む。
        plot.blockState("2 [0,1] [0,2]", ModBlocks.BIG_INTEGER_CPU.get().defaultBlockState());
        plot.block("2 1 0", AEBlocks.CRAFTING_ACCELERATOR);
        plot.blockState("3 0 0", ModBlocks.QUANTUM_CPU.get().defaultBlockState());

        plot.test(helper -> {
            // ACO が無ければ<b>何も検査しない</b>。
            // この 2 本は「ACO が long を超える BigInteger 計画を作れる」ことが前提で、
            // ACO 無しでは AE2 が素直に計画を作れず、失敗しても意味が読めない。
            if (optionalClass("com.syaru.ae2craftingoptimizer.api.big.BigCraftingEngineApi") == null) {
                helper.startSequence().thenSucceed();
                return;
            }
            var state = new Object() {
                appeng.me.helpers.MachineSource source;
                java.util.concurrent.Future<appeng.api.networking.crafting.ICraftingPlan> plan;
                long firstSample;
            };
            var sequence = helper.startSequence();

            sequence.thenExecute(() -> {
                var cpu = (QuantumCpuBlockEntity) helper.getBlockEntity(new BlockPos(3, 0, 0));
                cpu.getLogic().getPatternInv().addItems(
                        CraftingPatternHelper.encodeShapelessCraftingRecipe(helper.getLevel(),
                                new ItemStack(Items.OAK_LOG)));
                cpu.getLogic().getPatternInv().addItems(
                        CraftingPatternHelper.encodeShapelessCraftingRecipe(helper.getLevel(),
                                new ItemStack(Items.OAK_PLANKS)));
                for (int i = 0; i < QuantumCpuBlockEntity.MAX_ACCELERATION_CARDS; i++) {
                    cpu.getUpgrades().addItems(
                            new ItemStack(ModUpgrades.QUANTUM_ACCELERATION_CARD.get()));
                }
                cpu.getUpgrades().addItems(new ItemStack(ModUpgrades.TASK_FUSION_CARD.get()));
            });
            // ACO の compiled graph と generation が落ち着くまで待つ。
            sequence.thenIdle(60);

            // TestCraftingJob は失敗理由を「failed to submit job」としか言わないので、
            // ここは自分で計算 → 投入して<b>断られた理由</b>を出す。
            sequence.thenExecute(() -> {
                var grid = helper.getGrid(BlockPos.ZERO);
                state.source = new appeng.me.helpers.MachineSource(
                        (appeng.api.networking.security.IActionHost)
                                helper.getBlockEntity(new BlockPos(3, 0, 0)));
                state.plan = grid.getCraftingService().beginCraftingCalculation(
                        helper.getLevel(), () -> state.source,
                        AEItemKey.of(Items.OAK_BUTTON), requested,
                        appeng.api.networking.crafting.CalculationStrategy.REPORT_MISSING_ITEMS);
            });
            sequence.thenWaitUntil(() -> helper.check(state.plan.isDone(), "計算が終わらない"));
            sequence.thenExecute(() -> {
                appeng.api.networking.crafting.ICraftingPlan plan;
                try {
                    plan = state.plan.get();
                } catch (Exception failure) {
                    throw new GameTestAssertException("計算が例外で終わった: " + failure);
                }
                helper.check(!plan.simulation(),
                        "計算がシミュレーション止まり (素材不足扱い)。"
                                + "ACO の判断: " + insaneae$acoPlanDiagnostics());
                var result = helper.getGrid(BlockPos.ZERO).getCraftingService()
                        .submitJob(plan, null, null, false, state.source);
                var cpuSizes = new StringBuilder();
                for (var cpu : helper.getGrid(BlockPos.ZERO).getCraftingService().getCpus()) {
                    cpuSizes.append(" [available=").append(cpu.getAvailableStorage())
                            .append(" total=").append(cpu.getAvailableStorage())
                            .append(" busy=").append(cpu.isBusy());
                    // long に飽和した available だけでは足りるか分からないので、
                    // 正確な容量 (こちらの BigInteger 会計) も並べる。
                    if (cpu instanceof jp.main.taikun.insaneae.crafting.IBigCraftingCapacity exact) {
                        cpuSizes.append(" exactCapacity=").append(exact.insaneae$exactStorageCapacity());
                    }
                    cpuSizes.append(']');
                }
                helper.check(result.successful(),
                        "long を超える要求の投入が断られた: errorCode=" + result.errorCode()
                                + " 必要bytes=" + plan.bytes()
                                + " 正確な必要bytes=" + insaneae$exactPlanBytes(plan)
                                + " CPU=" + cpuSizes
                                + " ACOの判断=" + insaneae$acoPlanDiagnostics()
                                + " graph=" + insaneae$acoGraphProbe(helper,
                                        Items.OAK_BUTTON, Items.OAK_PLANKS, Items.OAK_LOG));
            });

            // 走り出しているか。
            sequence.thenIdle(20);
            sequence.thenExecute(() -> {
                state.firstSample = insaneae$storedAmount(helper, Items.OAK_BUTTON);
                helper.check(state.firstSample > 0,
                        "long を超える要求で完成品が 1 つも出てこない "
                                + "(投入は通ったのに実行が始まっていない)");
            });

            // 進んでいるか、または既に終わっているか。
            //
            // <b>「増えていること」だけを見てはいけない。</b>Quantum CPU はこの規模を
            // 数 tick で作りきってしまうので、最初の標本が既に要求量に達していることがある。
            // そのとき「40 tick 経っても増えない」は<b>停止ではなく完了</b>である。
            // (置き場が満杯でも増えなくなる。完成品は 8E セルに入れてあるので、
            //  ここで頭打ちになるなら本当に作り終えたとき。)
            sequence.thenIdle(40);
            sequence.thenExecute(() -> {
                long second = insaneae$storedAmount(helper, Items.OAK_BUTTON);
                helper.check(second >= state.firstSample,
                        "完成品が減っている (" + state.firstSample + " → " + second + ")");
                boolean finished = second >= requested;
                helper.check(finished || second > state.firstSample,
                        "long を超える要求が途中で止まっている (" + state.firstSample
                                + " から 40 tick 経っても " + second + " のまま。"
                                + "要求は " + requested + " なので未完了)");
            });
            sequence.thenSucceed();
        // 既定の制限時間だと足りない。投入が通るようになったぶん
        // 最後まで走るので、待ち time の合計 (60 + 計算 + 20 + 40) を賄う。
        }).maxTicks(400);
    }

    /**
     * <b>long を超える計画を「加工パターン」で頼んだときは、はっきり断ること。</b>
     *
     * <p>{@link #craftPastLong} と同じ規模・同じ木を、加工パターンで組んだもの。
     * ただし<b>期待する結果は逆</b>で、こちらは<b>投入が
     * {@code INCOMPLETE_PLAN} で断られるのが正解</b>。</p>
     *
     * <p>理由は {@code CraftingCpuLogicMixin} にある。ACO の BigInteger 計画を
     * 実際に回せるのはこちらの Quantum CPU だけで、Quantum CPU が扱えるのは
     * <b>クラフトテーブル用パターンだけ</b> ({@code IMolecularAssemblerSupportedPattern})。
     * 加工パターンが混じった計画をそのまま受けると、飽和した long のタスクが
     * <b>永久に終わらないジョブ</b>になって CPU を占有する。
     * だから受理せず、AE2 の明示的な失敗として返している。</p>
     *
     * <p><b>このテストが緑 = 「危ないものを黙って受けない」が守れている</b>ということ。
     * 逆にここが投入成功に変わったら、それは退行を疑う場所。</p>
     *
     * <p>(元はクラフトパターンが wide plan に載らない原因を切り分けるための
     * 対照実験だった。その疑いは外れ、今は上記の設計を守る回帰テストになっている。)</p>
     */
    @TestPlot("insaneae_craft_past_long_processing")
    public static void craftPastLongProcessing(PlotBuilder plot) {
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("[0,3] 0 0");
        plot.blockEntity("1 0 0", AEBlocks.DRIVE, drive -> {
            drive.getInternalInventory().addItems(insaneae$ultraCreativeCell(Items.OAK_LOG));
            // 完成品の置き場。64K セルだと 1 種類で 520,192 個 ((65536-512)*8) で満杯になり、
            // クラフトが「途中で止まった」ようにしか見えない。8E セルなら 3.6e19 個入るので
            // Long.MAX_VALUE の注文でも置き場が先に尽きない。
            drive.getInternalInventory().addItems(new ItemStack(
                    ModCells.ITEM_CELLS.get(InsaneCraftingUnitType.STORAGE_8E).get()));
        });
        // 1 個では足りない。この注文の正確な必要量は 3.2e19 bytes で、
        // このブロック 1 個の容量 (2^63-1 = 9.2e18) の約 3.5 倍ある。
        // 足りないまま投入すると CPU_TOO_SMALL で断られ、
        // <b>ACO 側の不具合と見分けが付かない</b>ので、余裕を持って 5 個積む。
        plot.blockState("2 [0,1] [0,2]", ModBlocks.BIG_INTEGER_CPU.get().defaultBlockState());
        plot.block("2 1 0", AEBlocks.CRAFTING_ACCELERATOR);
        plot.block("3 0 0", AEBlocks.PATTERN_PROVIDER);

        plot.test(helper -> {
            // ACO が無ければ<b>何も検査しない</b>。
            // この 2 本は「ACO が long を超える BigInteger 計画を作れる」ことが前提で、
            // ACO 無しでは AE2 が素直に計画を作れず、失敗しても意味が読めない。
            if (optionalClass("com.syaru.ae2craftingoptimizer.api.big.BigCraftingEngineApi") == null) {
                helper.startSequence().thenSucceed();
                return;
            }
            var state = new Object() {
                appeng.me.helpers.MachineSource source;
                java.util.concurrent.Future<appeng.api.networking.crafting.ICraftingPlan> plan;
            };
            var sequence = helper.startSequence();

            sequence.thenExecute(() -> {
                var provider = (appeng.blockentity.crafting.PatternProviderBlockEntity)
                        helper.getBlockEntity(new BlockPos(3, 0, 0));
                var patterns = provider.getLogic().getPatternInv();
                // craftPastLong と同じ 2 段: 原木 -> 板材 -> ボタン。
                patterns.addItems(processingPattern(Items.OAK_LOG, 1, Items.OAK_PLANKS, 4));
                patterns.addItems(processingPattern(Items.OAK_PLANKS, 1, Items.OAK_BUTTON, 1));
            });
            // ACO の compiled graph と generation が落ち着くまで待つ。
            sequence.thenIdle(60);

            sequence.thenExecute(() -> {
                var grid = helper.getGrid(BlockPos.ZERO);
                state.source = new appeng.me.helpers.MachineSource(
                        (appeng.api.networking.security.IActionHost)
                                helper.getBlockEntity(new BlockPos(3, 0, 0)));
                state.plan = grid.getCraftingService().beginCraftingCalculation(
                        helper.getLevel(), () -> state.source,
                        AEItemKey.of(Items.OAK_BUTTON), Long.MAX_VALUE,
                        appeng.api.networking.crafting.CalculationStrategy.REPORT_MISSING_ITEMS);
            });
            sequence.thenWaitUntil(() -> helper.check(state.plan.isDone(), "計算が終わらない"));
            sequence.thenExecute(() -> {
                appeng.api.networking.crafting.ICraftingPlan plan;
                try {
                    plan = state.plan.get();
                } catch (Exception failure) {
                    throw new GameTestAssertException("計算が例外で終わった: " + failure);
                }
                helper.check(!plan.simulation(),
                        "加工パターンでも計算がシミュレーション止まり。ACO の判断: "
                                + insaneae$acoPlanDiagnostics());
                var result = helper.getGrid(BlockPos.ZERO).getCraftingService()
                        .submitJob(plan, null, null, false, state.source);
                // <b>断られるのが正解。</b>詳細は上のクラス説明を参照。
                helper.check(!result.successful(),
                        "加工パターンの BigInteger 計画を受理してしまった。"
                                + "Quantum CPU は加工パターンを回せないので、"
                                + "受けると終わらないジョブが CPU を占有する");
                helper.check(result.errorCode()
                                == appeng.api.networking.crafting.CraftingSubmitErrorCode
                                        .INCOMPLETE_PLAN,
                        "断り方が想定と違う: errorCode=" + result.errorCode()
                                + " (INCOMPLETE_PLAN を期待。"
                                + "正確な必要bytes=" + insaneae$exactPlanBytes(plan) + ")");
            });
            sequence.thenSucceed();
        // 既定の制限時間だと足りない。投入が通るようになったぶん
        // 最後まで走るので、待ち time の合計 (60 + 計算 + 20 + 40) を賄う。
        }).maxTicks(400);
    }

    /**
     * ACO が BigInteger 計画を断った理由。{@code /aco stats} に出るのと同じ内容。
     *
     * <p>反射なのは、この検査クラスが ACO 無しの環境でも読まれるため。</p>
     */
    private static String insaneae$acoPlanDiagnostics() {
        try {
            Class<?> diagnostics = Class.forName(
                    "com.syaru.ae2craftingoptimizer.optimization.BigIntegerPlanDiagnostics",
                    false, InsaneAETestPlots.class.getClassLoader());
            Object lines = diagnostics.getMethod("summaryLines").invoke(null);
            return String.valueOf(lines);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) {
            return "(ACO の診断を読めない: " + unavailable + ")";
        }
    }

    /**
     * ACO の compiled graph が、その木をどう見ているかを覗く。
     *
     * <p>{@code NO_COMPILED_PROGRAM} は「{@code CompiledRootProgram} が組めなかった」としか
     * 言わないので、組めない条件 (パターンが 1 つでない / 循環 / 不完全) のどれなのかを
     * ここで直接聞く。全部 ACO の public メソッドだが、ACO 無しでも読めるよう反射で呼ぶ。</p>
     */
    private static String insaneae$acoGraphProbe(appeng.server.testworld.PlotTestHelper helper,
            net.minecraft.world.level.ItemLike... keys) {
        try {
            Class<?> cache = Class.forName(
                    "com.syaru.ae2craftingoptimizer.engine.Ae2CompiledCraftingGraphCache",
                    false, InsaneAETestPlots.class.getClassLoader());
            Object snapshot = cache.getMethod("getOrCompile",
                            appeng.api.networking.IGrid.class, net.minecraft.world.level.Level.class)
                    .invoke(null, helper.getGrid(BlockPos.ZERO), helper.getLevel());
            Class<?> snapType = snapshot.getClass();
            var count = snapType.getMethod("registeredPatternCount", AEKey.class);
            var oneFull = snapType.getMethod("hasExactlyOneFullyCompiledPattern", AEKey.class);
            var incomplete = snapType.getMethod("isIncompletelyCompiled", AEKey.class);
            var root = snapType.getMethod("rootProgram", AEKey.class);
            var craftables = snapType.getMethod("craftables");
            var graph = snapType.getMethod("graph").invoke(snapshot);
            var cyclic = graph.getClass().getMethod("isCyclic", Object.class);

            var report = new StringBuilder();
            report.append("craftables=").append(((java.util.Set<?>) craftables.invoke(snapshot)).size());
            for (var item : keys) {
                AEKey key = AEItemKey.of(item.asItem());
                report.append(" | ").append(item.asItem())
                        .append(": patterns=").append(count.invoke(snapshot, key))
                        .append(" oneFullyCompiled=").append(oneFull.invoke(snapshot, key))
                        .append(" incomplete=").append(incomplete.invoke(snapshot, key))
                        .append(" cyclic=").append(cyclic.invoke(graph, key))
                        .append(" rootProgram=")
                        .append(((java.util.Optional<?>) root.invoke(snapshot, key)).isPresent());
            }
            return report.toString();
        } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) {
            return "(ACO の compiled graph を覗けない: " + unavailable + ")";
        }
    }

    /** Advanced AE のブロック。入っていなければ null。 */
    @Nullable
    private static net.minecraft.world.level.block.Block insaneae$aaeBlock(String id) {
        var key = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("advanced_ae", id);
        if (!net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(key)) {
            return null;
        }
        var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(key);
        return block == net.minecraft.world.level.block.Blocks.AIR ? null : block;
    }

    /**
     * @param configureOutputToo クリエイティブセルに完成品 (板材) も設定するか
     */
    private static void insaneae$craftCompletionPlot(PlotBuilder plot, boolean configureOutputToo) {
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("[0,3] 0 0");
        plot.blockEntity("1 0 0", AEBlocks.DRIVE, drive -> {
            // 材料は超強化クリエイティブセルから。完成品の置き場に普通のセルを 1 枚。
            drive.getInternalInventory().addItems(configureOutputToo
                    ? insaneae$ultraCreativeCell(Items.OAK_LOG, Items.OAK_PLANKS)
                    : insaneae$ultraCreativeCell(Items.OAK_LOG));
            drive.getInternalInventory().addItems(AEItems.ITEM_CELL_64K.stack());
        });
        plot.block("2 0 0", AEBlocks.CRAFTING_STORAGE_64K);
        plot.block("2 1 0", AEBlocks.CRAFTING_ACCELERATOR);
        plot.blockState("3 0 0", ModBlocks.QUANTUM_CPU.get().defaultBlockState());

        // 少量にしてあるのは「速いか」ではなく「終わるか」を見るテストだから。
        final long requested = 64;

        plot.test(helper -> {
            var state = new Object() {
                appeng.server.testworld.TestCraftingJob job;
            };
            var sequence = helper.startSequence();

            sequence.thenExecute(() -> {
                var cpu = (QuantumCpuBlockEntity) helper.getBlockEntity(new BlockPos(3, 0, 0));
                cpu.getLogic().getPatternInv().addItems(
                        CraftingPatternHelper.encodeShapelessCraftingRecipe(helper.getLevel(),
                                new ItemStack(Items.OAK_LOG)));
            });
            sequence.thenIdle(5);

            sequence.thenExecute(() -> state.job = new appeng.server.testworld.TestCraftingJob(
                    helper, BlockPos.ZERO, AEItemKey.of(Items.OAK_PLANKS), requested));
            sequence.thenWaitUntil(() -> state.job.tickUntilStarted());

            // 完成待ちが減って<b>要求量がまるごとネットワークに載る</b>まで待つ。
            // 途中で消えていると、ここで時間切れになって落ちる (= 症状の再現)。
            sequence.thenWaitUntil(() -> {
                long stored = insaneae$storedAmount(helper, Items.OAK_PLANKS);
                if (stored < requested) {
                    throw new GameTestAssertException("板材が " + stored + "/" + requested
                            + " しかネットワークに無い"
                            + (configureOutputToo
                                    ? " (完成品もクリエイティブセルに設定してある並び"
                                            + " — セルが完成品を飲んでいる疑い)"
                                    : ""));
                }
            });

            // クラフト CPU が仕事を抱えたままになっていないこと。
            // タスクだけ空になって「進行中」のまま止まる症状は、ここで捕まる。
            sequence.thenWaitUntil(() -> {
                for (var cpu : helper.getGrid(BlockPos.ZERO).getCraftingService().getCpus()) {
                    if (cpu.isBusy()) {
                        throw new GameTestAssertException(
                                "完成品は揃ったのにクラフト CPU がジョブを抱えたまま "
                                        + "(完成待ちが減っていない)");
                    }
                }
            });
            sequence.thenSucceed();
        });
    }

    /** 中身を設定した超強化クリエイティブセルを 1 枚作る。 */
    private static ItemStack insaneae$ultraCreativeCell(net.minecraft.world.level.ItemLike... contents) {
        ItemStack cell = new ItemStack(ModCells.ULTRA_CREATIVE_CELL.get());
        var config = appeng.items.contents.CellConfig.create(cell);
        for (int i = 0; i < contents.length; i++) {
            config.setStack(i, new appeng.api.stacks.GenericStack(
                    AEItemKey.of(contents[i].asItem()), 1));
        }
        return cell;
    }

    /** ネットワーク全体に載っているアイテム数。 */
    private static long insaneae$storedAmount(appeng.server.testworld.PlotTestHelper helper,
            net.minecraft.world.level.ItemLike item) {
        return helper.getGrid(BlockPos.ZERO).getStorageService().getInventory()
                .getAvailableStacks().get(AEItemKey.of(item.asItem()));
    }

    @TestPlot("insaneae_bulk_execution_live")
    public static void bulkExecutionLive(PlotBuilder plot) {
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("[0,3] 0 0");
        plot.blockEntity("1 0 0", AEBlocks.DRIVE, drive -> {
            // 材料は無限、完成品の置き場に普通のセルを 1 枚。
            drive.getInternalInventory().addItems(CreativeCellItem.ofItems(Items.OAK_LOG));
            drive.getInternalInventory().addItems(AEItems.ITEM_CELL_64K.stack());
        });
        plot.block("2 0 0", AEBlocks.CRAFTING_STORAGE_64K);
        plot.block("2 1 0", AEBlocks.CRAFTING_ACCELERATOR);
        plot.blockState("3 0 0", ModBlocks.QUANTUM_CPU.get().defaultBlockState());

        final long crafts = 1000;
        final int planksPerCraft = 4;

        plot.test(helper -> {
            var state = new Object() {
                long windowsBefore;
                appeng.server.testworld.TestCraftingJob job;
            };
            var sequence = helper.startSequence();

            sequence.thenExecute(() -> {
                var cpu = (QuantumCpuBlockEntity) helper.getBlockEntity(new BlockPos(3, 0, 0));
                cpu.getLogic().getPatternInv().addItems(
                        CraftingPatternHelper.encodeShapelessCraftingRecipe(helper.getLevel(),
                                new ItemStack(Items.OAK_LOG)));
                cpu.getUpgrades().addItems(new ItemStack(ModUpgrades.TASK_FUSION_CARD.get()));
                cpu.getUpgrades().addItems(new ItemStack(ModUpgrades.QUANTUM_ACCELERATION_CARD.get()));
            });

            // パターンの読み直しとクラフト索引の更新待ち (他のまとめ処理テストと同じ)。
            sequence.thenIdle(5);

            sequence.thenExecute(() -> {
                var cpu = (QuantumCpuBlockEntity) helper.getBlockEntity(new BlockPos(3, 0, 0));
                helper.check(cpu.getLogic().getAvailablePatterns().size() == 1,
                        "Quantum CPU がパターンを 1 枚だけ持っている状態にならなかった");
                state.windowsBefore = jp.main.taikun.insaneae.quantum.QuantumBulkCrafting.bulkWindows;
                state.job = new appeng.server.testworld.TestCraftingJob(helper, BlockPos.ZERO,
                        AEItemKey.of(Items.OAK_PLANKS), crafts * planksPerCraft);
            });

            sequence.thenWaitUntil(() -> state.job.tickUntilStarted());
            sequence.thenIdle(20);

            sequence.thenExecute(() -> helper.check(
                    jp.main.taikun.insaneae.quantum.QuantumBulkCrafting.bulkWindows > state.windowsBefore,
                    "実ジョブでまとめ処理が一度も走っていない"
                            + " (executeCrafting への注入が他 Mod に先取りされている可能性)"));

            sequence.thenWaitUntil(
                    () -> helper.assertContains(helper.getGrid(BlockPos.ZERO), Items.OAK_PLANKS));
            sequence.thenSucceed();
        });
    }

    /**
     * {@link CraftingJobView} の最小の実装。タスクは 1 つだけ持つ。
     * 在庫をこちらで固定できるので、まとめ処理の入出力を直接検算できる。
     */
    private static final class FakeJobView implements CraftingJobView {

        final appeng.crafting.inv.ListCraftingInventory inventory =
                new appeng.crafting.inv.ListCraftingInventory(what -> {
                });
        final appeng.crafting.inv.ListCraftingInventory waitingFor =
                new appeng.crafting.inv.ListCraftingInventory(what -> {
                });
        final appeng.crafting.execution.ElapsedTimeTracker tracker =
                new appeng.crafting.execution.ElapsedTimeTracker();

        final appeng.api.crafting.IPatternDetails details;
        long remaining;
        boolean removed;
        /** null でなければ Exact (BigInteger) 経路を通す。 */
        jp.main.taikun.insaneae.integration.aco.AcoBigIntegerJobRegistry.CraftingCursor exactCursor;

        FakeJobView(appeng.api.crafting.IPatternDetails details, long remaining) {
            this.details = details;
            this.remaining = remaining;
        }

        @Override
        public appeng.crafting.inv.ListCraftingInventory getInventory() {
            return inventory;
        }

        @Override
        public appeng.crafting.inv.ListCraftingInventory getWaitingFor() {
            return waitingFor;
        }

        @Override
        public appeng.crafting.execution.ElapsedTimeTracker getTimeTracker() {
            return tracker;
        }

        @Override
        public void markDirty() {
        }

        @Override
        public java.util.Optional<
                jp.main.taikun.insaneae.integration.aco.AcoBigIntegerJobRegistry.CraftingCursor>
                exactTasks() {
            return java.util.Optional.ofNullable(exactCursor);
        }

        @Override
        public TaskCursor tasks() {
            return new TaskCursor() {
                private boolean served;

                @Override
                public boolean next() {
                    if (served || removed) {
                        return false;
                    }
                    served = true;
                    return true;
                }

                @Override
                public appeng.api.crafting.IPatternDetails details() {
                    return details;
                }

                @Override
                public long remaining() {
                    return remaining;
                }

                @Override
                public void setRemaining(long value) {
                    remaining = value;
                }

                @Override
                public void remove() {
                    removed = true;
                }
            };
        }
    }

    private static ItemStack processingPattern(net.minecraft.world.item.Item input, long inputAmount,
            net.minecraft.world.item.Item output, long outputAmount) {
        // 19.2 で配列ではなく List を取るようになった。
        return appeng.api.crafting.PatternDetailsHelper.encodeProcessingPattern(
                java.util.List.of(new appeng.api.stacks.GenericStack(AEItemKey.of(input), inputAmount)),
                java.util.List.of(new appeng.api.stacks.GenericStack(AEItemKey.of(output), outputAmount)));
    }

    /**
     * 計算のまとめ処理が働いたか。
     *
     * <p>ACO 同居中は<b>働かないのが正しい</b> — 厳密計算は ACO が所有し、こちらの計算バッチは
     * {@code AcoCalculationIntegration} で譲るため。そのときは「結果が AE2 と一致すること」
     * だけを見る (実行側のまとめ処理は譲らない — {@code insaneae_bulk_execution_live} が見ている)。</p>
     */
    private static boolean insaneBatchingRan(long before) {
        if (AcoCalculationIntegration.shouldDeferCalculationBatch()) {
            return true;
        }
        return CraftingCalculationBatch.batchedCrafts > before;
    }

    private static Future<ICraftingPlan> beginCalculation(appeng.server.testworld.PlotTestHelper helper) {
        return beginCalculation(helper, AEItemKey.of(Items.CAKE), CAKES);
    }

    private static Future<ICraftingPlan> beginCalculation(appeng.server.testworld.PlotTestHelper helper,
            AEItemKey what, long amount) {
        var grid = helper.getGrid(BlockPos.ZERO);
        var source = new MachineSource(grid::getPivot);
        ICraftingSimulationRequester requester = () -> source;
        return grid.getCraftingService().beginCraftingCalculation(
                grid.getPivot().getLevel(), requester, what, amount,
                CalculationStrategy.REPORT_MISSING_ITEMS);
    }

    /** 計算が終わるまで待つ ({@code thenWaitUntil} は例外が出なくなるまで繰り返す)。 */
    private static ICraftingPlan awaitPlan(Future<ICraftingPlan> pending) {
        if (!pending.isDone()) {
            throw new GameTestAssertException("クラフト計算がまだ終わっていない");
        }
        try {
            return pending.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new GameTestAssertException("クラフト計算に失敗した: " + e);
        }
    }

    private static void checkSameCounts(appeng.server.testworld.PlotTestHelper helper, String what,
            KeyCounter expected, KeyCounter actual) {
        Map<AEKey, Long> left = toMap(expected);
        Map<AEKey, Long> right = toMap(actual);
        helper.check(left.equals(right), what + "が違う: " + left + " → " + right);
    }

    private static void checkSamePatternTimes(appeng.server.testworld.PlotTestHelper helper,
            ICraftingPlan expected, ICraftingPlan actual) {
        Map<String, Long> left = new HashMap<>();
        expected.patternTimes().forEach((pattern, times) -> left.merge(
                pattern.getDefinition().toString(), times, Long::sum));
        Map<String, Long> right = new HashMap<>();
        actual.patternTimes().forEach((pattern, times) -> right.merge(
                pattern.getDefinition().toString(), times, Long::sum));
        helper.check(left.equals(right), "パターンの実行回数が違う: " + left + " → " + right);
    }

    private static Map<AEKey, Long> toMap(KeyCounter counter) {
        Map<AEKey, Long> map = new HashMap<>();
        for (var entry : counter) {
            if (entry.getLongValue() != 0) {
                map.put(entry.getKey(), entry.getLongValue());
            }
        }
        return map;
    }
}

