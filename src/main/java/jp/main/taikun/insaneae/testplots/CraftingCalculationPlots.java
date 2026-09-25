package jp.main.taikun.insaneae.testplots;

import static jp.main.taikun.insaneae.testplots.TestPlotSupport.processingPattern;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.items.storage.CreativeCellItem;
import appeng.me.helpers.MachineSource;
import appeng.server.testplots.CraftingPatternHelper;
import appeng.server.testplots.TestPlot;
import appeng.server.testplots.TestPlotClass;
import appeng.server.testplots.TestPlots;
import appeng.server.testworld.PlotBuilder;
import appeng.server.testworld.PlotTestHelper;
import jp.main.taikun.insaneae.config.InsaneAEConfig;
import jp.main.taikun.insaneae.crafting.CraftingCalculationBatch;
import jp.main.taikun.insaneae.integration.aco.AcoCalculationIntegration;
import jp.main.taikun.insaneae.provider.InsanePatternProviderBlockEntity;
import jp.main.taikun.insaneae.provider.InsanePatternProviderLogic;
import jp.main.taikun.insaneae.registries.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * クラフト計算のまとめ処理 (AE2 本来の計算と同じ結果になるか、long あふれの門番)。
 *
 * <p>走らせ方は {@link TestPlotSupport} を参照。</p>
 */
@TestPlotClass
public final class CraftingCalculationPlots {

    private CraftingCalculationPlots() {
    }

    /** ケーキ 1 個につきバケツ 3 個が行き来する = コンテナアイテム持ちのパターン。 */
    private static final long CAKES = 4096;

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
    private static void checkOverflowRejected(PlotTestHelper helper,
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

    private static Future<ICraftingPlan> beginCalculation(PlotTestHelper helper) {
        return beginCalculation(helper, AEItemKey.of(Items.CAKE), CAKES);
    }

    private static Future<ICraftingPlan> beginCalculation(PlotTestHelper helper,
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

    private static void checkSameCounts(PlotTestHelper helper, String what,
            KeyCounter expected, KeyCounter actual) {
        Map<AEKey, Long> left = toMap(expected);
        Map<AEKey, Long> right = toMap(actual);
        helper.check(left.equals(right), what + "が違う: " + left + " → " + right);
    }

    private static void checkSamePatternTimes(PlotTestHelper helper,
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
