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

