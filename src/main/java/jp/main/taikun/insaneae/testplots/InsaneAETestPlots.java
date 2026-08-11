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
import jp.main.taikun.insaneae.provider.InsanePatternProviderBlockEntity;
import jp.main.taikun.insaneae.quantum.CraftingJobView;
import jp.main.taikun.insaneae.quantum.QuantumCpuBlockEntity;
import jp.main.taikun.insaneae.registries.ModBlocks;
import jp.main.taikun.insaneae.registries.ModCells;
import jp.main.taikun.insaneae.registries.ModUpgrades;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;
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
                helper.check(CraftingCalculationBatch.batchedCrafts > state.batchedBefore,
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

                helper.check(CraftingCalculationBatch.batchedCrafts > state.batchedBefore,
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

