package jp.main.taikun.insaneae.testplots;

import static jp.main.taikun.insaneae.testplots.TestPlotSupport.acoPlanDiagnostics;
import static jp.main.taikun.insaneae.testplots.TestPlotSupport.optionalBlock;
import static jp.main.taikun.insaneae.testplots.TestPlotSupport.optionalClass;
import static jp.main.taikun.insaneae.testplots.TestPlotSupport.processingPattern;
import static jp.main.taikun.insaneae.testplots.TestPlotSupport.storedAmount;
import static jp.main.taikun.insaneae.testplots.TestPlotSupport.ultraCreativeCell;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.CraftingSubmitErrorCode;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionHost;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.me.helpers.MachineSource;
import appeng.server.testplots.CraftingPatternHelper;
import appeng.server.testplots.TestPlot;
import appeng.server.testplots.TestPlotClass;
import appeng.server.testworld.PlotBuilder;
import appeng.server.testworld.PlotTestHelper;
import appeng.server.testworld.TestCraftingJob;
import jp.main.taikun.insaneae.crafting.IBigCraftingCapacity;
import jp.main.taikun.insaneae.crafting.InsaneCraftingUnitType;
import jp.main.taikun.insaneae.integration.aco.AcoBigIntegerPlanBridge;
import jp.main.taikun.insaneae.integration.aco.AcoClassNames;
import jp.main.taikun.insaneae.quantum.QuantumBulkCrafting;
import jp.main.taikun.insaneae.quantum.QuantumCpuBlockEntity;
import jp.main.taikun.insaneae.registries.ModBlocks;
import jp.main.taikun.insaneae.registries.ModCells;
import jp.main.taikun.insaneae.registries.ModUpgrades;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import java.math.BigInteger;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;

/**
 * クリエイティブセルからのクラフトと、long を超える量のクラフトを完走できるか。
 *
 * <p>走らせ方は {@link TestPlotSupport} を参照。</p>
 */
@TestPlotClass
public final class LongOverflowCraftPlots {

    private LongOverflowCraftPlots() {
    }

    /**
     * ACO の BigInteger 計画が持つ<b>正確な</b>必要バイト数。
     *
     * <p>{@code plan.bytes()} は long に飽和するので、断られた理由が
     * 「BigInteger 計画が作れなかった」のか「作れたが容量が足りない」のかを
     * 区別できない。ここが {@code <BigInteger計画なし>} なら前者。</p>
     */
    private static String exactPlanBytes(ICraftingPlan plan) {
        try {
            return AcoBigIntegerPlanBridge.inspect(plan)
                    .map(exact -> exact.exactBytes().toString())
                    .orElse("<BigInteger計画なし>");
        } catch (RuntimeException | LinkageError unavailable) {
            return "<読めず: " + unavailable + ">";
        }
    }

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
        craftCompletionPlot(plot, false);
    }

    /** {@link #craftFromCreativeCell} の並び 2 — 完成品もセルに設定してある場合。 */
    @TestPlot("insaneae_craft_output_also_in_cell")
    public static void craftOutputAlsoInCell(PlotBuilder plot) {
        craftCompletionPlot(plot, true);
    }

    /**
     * <b>同じアイテムを申告するセルが 2 枚あっても、在庫が負数へ折り返さないこと。</b>
     *
     * <p>{@code KeyCounter} はキーごとに long。クリエイティブセルは設定した種類を
     * {@code Long.MAX_VALUE} で申告するので、素朴に足すと 2 枚目で折り返す。
     * 負の在庫を見た ACO は「正確値を復元できない」として計画ごと降りるため
     * ({@code WidePlanUnavailableException: BigInteger inventory sidecar is incomplete})、
     * <b>セルを 1 枚足しただけであらゆるクラフトが失敗する</b>。実機では
     * ExtendedAE Plus の Infinity セルと同居させたときに発生した。</p>
     */
    @TestPlot("insaneae_creative_cell_no_overflow")
    public static void creativeCellNoOverflow(PlotBuilder plot) {
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("[0,1] 0 0");
        plot.blockEntity("1 0 0", AEBlocks.DRIVE, drive -> {
            // 同じ種類を申告するセルを 2 枚。実機の「無限セル 2 枚」を最小構成で再現する。
            drive.getInternalInventory().addItems(ultraCreativeCell(Items.OAK_LOG));
            drive.getInternalInventory().addItems(ultraCreativeCell(Items.OAK_LOG));
        });

        plot.test(helper -> {
            var sequence = helper.startSequence();
            sequence.thenIdle(5);
            sequence.thenExecute(() -> {
                long stored = storedAmount(helper, Items.OAK_LOG);
                helper.check(stored > 0,
                        "無限セル 2 枚で在庫が負数へ折り返した (" + stored + ")");
                helper.check(stored == Long.MAX_VALUE,
                        "在庫が long の天井になっていない (" + stored + ")");
            });
            sequence.thenSucceed();
        }).maxTicks(60);
    }

    /**
     * <b>中間素材の必要数が long を超える木を、最後まで作り切れること。</b>
     *
     * <p>{@link #craftPastLong} との違いは<b>どこが long を超えるか</b>。あちらは
     * 完成品の要求数だけが大きく、木を下りるほど数が減る (ボタン→板→原木)。
     * こちらは<b>途中の段が要求数より多くなる</b> (チェスト 1 個 = 板 8 枚) ので、
     * 完成品が long 内でも<b>中間素材が long を超える</b>。</p>
     *
     * <p>実機で 8 倍ずつ 100 段重ねた鎖を頼んだとき、<b>中間段が long を超える
     * ちょうどそこから</b>完成しなくなる症状が出た (22 段までは完走、23 段から停止)。
     * 既存のテストはどれも中間段が long 内なので、この形だけ穴になっていた。</p>
     */
    @TestPlot("insaneae_craft_past_long_intermediate")
    public static void craftPastLongIntermediate(PlotBuilder plot) {
        // チェスト 1 個 = 板 8 枚、原木 1 本 = 板 4 枚。
        // 要求 5e18 だと 板 = 4e19、<b>板パターンの実行回数 = 1e19</b> で、
        // どちらも Long.MAX_VALUE (9.22e18) を超える。
        // 回数が long 内だと ACO は通常の long 計画を返し、BigInteger 実行経路を
        // 通らない (= このテストが何も検査しないことになる) ので、ここは回数で選ぶ。
        final long requested = 5_000_000_000_000_000_000L;
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("[0,3] 0 0");
        plot.blockEntity("1 0 0", AEBlocks.DRIVE, drive -> {
            drive.getInternalInventory().addItems(ultraCreativeCell(Items.OAK_LOG));
            // 完成品の置き場。8E セルなら 3.6e19 個入るので要求数で先に満杯にならない。
            drive.getInternalInventory().addItems(new ItemStack(
                    ModCells.ITEM_CELLS.get(InsaneCraftingUnitType.STORAGE_8E).get()));
        });
        plot.blockState("2 [0,1] [0,2]", ModBlocks.BIG_INTEGER_CPU.get().defaultBlockState());
        plot.block("2 1 0", AEBlocks.CRAFTING_ACCELERATOR);
        plot.blockState("3 0 0", ModBlocks.QUANTUM_CPU.get().defaultBlockState());

        plot.test(helper -> {
            // ACO 無しではこの規模の計画自体が作れないので、何も検査しない。
            if (optionalClass(AcoClassNames.BIG_CRAFTING_ENGINE_API) == null) {
                helper.startSequence().thenSucceed();
                return;
            }
            var state = new Object() {
                MachineSource source;
                Future<ICraftingPlan> plan;
                long firstSample;
            };
            var sequence = helper.startSequence();

            sequence.thenExecute(() -> {
                var cpu = (QuantumCpuBlockEntity) helper.getBlockEntity(new BlockPos(3, 0, 0));
                cpu.getLogic().getPatternInv().addItems(
                        CraftingPatternHelper.encodeShapelessCraftingRecipe(helper.getLevel(),
                                new ItemStack(Items.OAK_LOG)));
                // 板 8 → チェスト 1。中央だけ空けた 3x3 のバニラレシピ。
                Object[] chest = new Object[] {
                        Items.OAK_PLANKS, Items.OAK_PLANKS, Items.OAK_PLANKS,
                        Items.OAK_PLANKS, null, Items.OAK_PLANKS,
                        Items.OAK_PLANKS, Items.OAK_PLANKS, Items.OAK_PLANKS};
                cpu.getLogic().getPatternInv().addItems(
                        CraftingPatternHelper.encodeCraftingPattern(
                                helper.getLevel(), chest, false, false));
                for (int i = 0; i < QuantumCpuBlockEntity.MAX_ACCELERATION_CARDS; i++) {
                    cpu.getUpgrades().addItems(
                            new ItemStack(ModUpgrades.QUANTUM_ACCELERATION_CARD.get()));
                }
                cpu.getUpgrades().addItems(new ItemStack(ModUpgrades.TASK_FUSION_CARD.get()));
            });
            sequence.thenIdle(60);

            sequence.thenExecute(() -> {
                var grid = helper.getGrid(BlockPos.ZERO);
                state.source = new MachineSource(
                        (IActionHost)
                                helper.getBlockEntity(new BlockPos(3, 0, 0)));
                state.plan = grid.getCraftingService().beginCraftingCalculation(
                        helper.getLevel(), () -> state.source,
                        AEItemKey.of(Items.CHEST), requested,
                        CalculationStrategy.REPORT_MISSING_ITEMS);
            });
            sequence.thenWaitUntil(() -> helper.check(state.plan.isDone(), "計算が終わらない"));
            sequence.thenExecute(() -> {
                ICraftingPlan plan;
                try {
                    plan = state.plan.get();
                } catch (Exception failure) {
                    throw new GameTestAssertException("計算が例外で終わった: " + failure);
                }
                helper.check(!plan.simulation(),
                        "計算がシミュレーション止まり (素材不足扱い)。"
                                + "ACO の判断: " + acoPlanDiagnostics());
                var result = helper.getGrid(BlockPos.ZERO).getCraftingService()
                        .submitJob(plan, null, null, false, state.source);
                helper.check(result.successful(),
                        "中間素材が long を超える要求の投入が断られた: errorCode="
                                + result.errorCode()
                                + " 正確な必要bytes=" + exactPlanBytes(plan)
                                + " ACOの判断=" + acoPlanDiagnostics());
            });

            // <b>tick 数まで見る。</b>加速カード満載 + タスク統合なら、この規模は
            // 数 tick で終わるのが仕様 (窓は 1 tick に 1024 枚使える)。段ごとに
            // tick を消費する実装だと 4 tick あたり 1 段しか進まず、この待ち時間では終わらない。
            sequence.thenIdle(2);
            sequence.thenExecute(() ->
                    state.firstSample = storedAmount(helper, Items.CHEST));
            sequence.thenIdle(6);
            sequence.thenExecute(() -> {
                long second = storedAmount(helper, Items.CHEST);
                helper.check(second >= state.firstSample,
                        "完成品が減っている (" + state.firstSample + " → " + second + ")");
                // 「増えている」だけでは不十分。実機の症状は「進むが終わらない」なので、
                // この規模なら数 tick で作り切れるはずの<b>完走</b>を要求する。
                helper.check(second >= requested,
                        "中間素材が long を超える木が数 tick で終わらない (" + state.firstSample
                                + " → " + second + "、要求は " + requested + ")。"
                                + "板の在庫=" + storedAmount(helper, Items.OAK_PLANKS)
                                + " 原木の在庫=" + storedAmount(helper, Items.OAK_LOG));
            });
            sequence.thenSucceed();
        }).maxTicks(600);
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
     * <h2>ACO 込みで落ちるとき</h2>
     * <p>ACO の版によっては、投入が {@code CPU_TOO_SMALL} で断られて落ちる。容量の問題ではなく、
     * 必要 bytes も CPU の空きも同じ {@code Long.MAX_VALUE} で、AE2 自身の判定
     * ({@code available >= bytes}) は通っている。断っているのは ACO の
     * {@code CraftingCpuClusterBigCapacityGuardMixin} で、wide plan に BigInteger の裏付けが無いときに
     * {@code CPU_TOO_SMALL} を返す。ACO の診断には理由が {@code NO_COMPILED_PROGRAM} と出る。</p>
     *
     * <p>パターンの種類 (クラフト / 加工) を変えても同じ理由で断られる ({@link #craftPastLongProcessing})。
     * {@link #acoGraphProbe} で ACO の compiled graph を直接読むと木は健全で ({@code rootProgram=true})、
     * 同じ {@code rootProgram(what)} の呼び出しもこちらでは値を返す。ACO の判断と ACO 自身の graph が
     * 食い違っているので、原因は ACO 側にあり、InsaneAE のパターンや在庫の出し方ではない。</p>
     *
     * <p><b>注意: ACO を載せるには {@code -PwithAco=true} と書くこと。</b>
     * {@code -PwithAco} だけだと値が空文字になり、build.gradle の {@code == 'true'} が
     * 偽になって<b>エラーなしで ACO 無しのまま走る</b> (テストは通ってしまう)。</p>
     */
    @TestPlot("insaneae_craft_past_long")
    public static void craftPastLong(PlotBuilder plot) {
        // 要求量。完了判定にも使うので 1 か所にまとめる。
        final long requested = Long.MAX_VALUE;
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("[0,3] 0 0");
        plot.blockEntity("1 0 0", AEBlocks.DRIVE, drive -> {
            drive.getInternalInventory().addItems(ultraCreativeCell(Items.OAK_LOG));
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
            if (optionalClass(AcoClassNames.BIG_CRAFTING_ENGINE_API) == null) {
                helper.startSequence().thenSucceed();
                return;
            }
            var state = new Object() {
                MachineSource source;
                Future<ICraftingPlan> plan;
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
                state.source = new MachineSource(
                        (IActionHost)
                                helper.getBlockEntity(new BlockPos(3, 0, 0)));
                state.plan = grid.getCraftingService().beginCraftingCalculation(
                        helper.getLevel(), () -> state.source,
                        AEItemKey.of(Items.OAK_BUTTON), requested,
                        CalculationStrategy.REPORT_MISSING_ITEMS);
            });
            sequence.thenWaitUntil(() -> helper.check(state.plan.isDone(), "計算が終わらない"));
            sequence.thenExecute(() -> {
                ICraftingPlan plan;
                try {
                    plan = state.plan.get();
                } catch (Exception failure) {
                    throw new GameTestAssertException("計算が例外で終わった: " + failure);
                }
                helper.check(!plan.simulation(),
                        "計算がシミュレーション止まり (素材不足扱い)。"
                                + "ACO の判断: " + acoPlanDiagnostics());
                var result = helper.getGrid(BlockPos.ZERO).getCraftingService()
                        .submitJob(plan, null, null, false, state.source);
                var cpuSizes = new StringBuilder();
                for (var cpu : helper.getGrid(BlockPos.ZERO).getCraftingService().getCpus()) {
                    cpuSizes.append(" [available=").append(cpu.getAvailableStorage())
                            .append(" total=").append(cpu.getAvailableStorage())
                            .append(" busy=").append(cpu.isBusy());
                    // long に飽和した available だけでは足りるか分からないので、
                    // 正確な容量 (こちらの BigInteger 会計) も並べる。
                    if (cpu instanceof IBigCraftingCapacity exact) {
                        cpuSizes.append(" exactCapacity=").append(exact.insaneae$exactStorageCapacity());
                    }
                    cpuSizes.append(']');
                }
                helper.check(result.successful(),
                        "long を超える要求の投入が断られた: errorCode=" + result.errorCode()
                                + " 必要bytes=" + plan.bytes()
                                + " 正確な必要bytes=" + exactPlanBytes(plan)
                                + " CPU=" + cpuSizes
                                + " ACOの判断=" + acoPlanDiagnostics()
                                + " graph=" + acoGraphProbe(helper,
                                        Items.OAK_BUTTON, Items.OAK_PLANKS, Items.OAK_LOG));
            });

            sequence.thenIdle(20);
            sequence.thenExecute(() -> {
                state.firstSample = storedAmount(helper, Items.OAK_BUTTON);
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
                long second = storedAmount(helper, Items.OAK_BUTTON);
                helper.check(second >= state.firstSample,
                        "完成品が減っている (" + state.firstSample + " → " + second + ")");
                boolean finished = second >= requested;
                helper.check(finished || second > state.firstSample,
                        "long を超える要求が途中で止まっている (" + state.firstSample
                                + " から 40 tick 経っても " + second + " のまま。"
                                + "要求は " + requested + " なので未完了)");
            });
            sequence.thenSucceed();
        // 既定の制限時間だと足りない。最後まで走るので、
        // 待ち時間の合計 (60 + 計算 + 20 + 40) を賄う。
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
     * 逆にここが投入成功に変わったら、リグレッションを疑うこと。</p>
     */
    @TestPlot("insaneae_craft_past_long_processing")
    public static void craftPastLongProcessing(PlotBuilder plot) {
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("[0,3] 0 0");
        plot.blockEntity("1 0 0", AEBlocks.DRIVE, drive -> {
            drive.getInternalInventory().addItems(ultraCreativeCell(Items.OAK_LOG));
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
            if (optionalClass(AcoClassNames.BIG_CRAFTING_ENGINE_API) == null) {
                helper.startSequence().thenSucceed();
                return;
            }
            var state = new Object() {
                MachineSource source;
                Future<ICraftingPlan> plan;
            };
            var sequence = helper.startSequence();

            sequence.thenExecute(() -> {
                var provider = (PatternProviderBlockEntity)
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
                state.source = new MachineSource(
                        (IActionHost)
                                helper.getBlockEntity(new BlockPos(3, 0, 0)));
                state.plan = grid.getCraftingService().beginCraftingCalculation(
                        helper.getLevel(), () -> state.source,
                        AEItemKey.of(Items.OAK_BUTTON), Long.MAX_VALUE,
                        CalculationStrategy.REPORT_MISSING_ITEMS);
            });
            sequence.thenWaitUntil(() -> helper.check(state.plan.isDone(), "計算が終わらない"));
            sequence.thenExecute(() -> {
                ICraftingPlan plan;
                try {
                    plan = state.plan.get();
                } catch (Exception failure) {
                    throw new GameTestAssertException("計算が例外で終わった: " + failure);
                }
                helper.check(!plan.simulation(),
                        "加工パターンでも計算がシミュレーション止まり。ACO の判断: "
                                + acoPlanDiagnostics());
                var result = helper.getGrid(BlockPos.ZERO).getCraftingService()
                        .submitJob(plan, null, null, false, state.source);
                helper.check(!result.successful(),
                        "加工パターンの BigInteger 計画を受理してしまった。"
                                + "Quantum CPU は加工パターンを回せないので、"
                                + "受けると終わらないジョブが CPU を占有する");
                helper.check(result.errorCode()
                                == CraftingSubmitErrorCode
                                        .INCOMPLETE_PLAN,
                        "断り方が想定と違う: errorCode=" + result.errorCode()
                                + " (INCOMPLETE_PLAN を期待。"
                                + "正確な必要bytes=" + exactPlanBytes(plan) + ")");
            });
            sequence.thenSucceed();
        // 既定の制限時間だと足りない。待ち 60 tick と計算の時間を賄う。
        }).maxTicks(400);
    }

    /**
     * <b>同じ long 超の注文を、ACC (Advanced Assembly Computing) の Vector 実行系で走らせる対照実験。</b>
     *
     * <p>{@link #craftPastLong} の実行側はうちの Quantum CPU (汎用の
     * {@code CraftingTableBatchTarget})。一方、ACO の物理実行
     * ({@code PhysicalCraftingTreeTransaction}) の待ち理由の文字列はほとんどが NeoECO を
     * 名指ししており、<b>本来想定されている実行系は ACC + NeoECO の Vector Crafting
     * System</b> と見ている (build.gradle の withAcc の説明を参照)。そこでこちらは
     * Quantum CPU を置かず、パターンを NeoECO のパターンバスへ入れて同じ注文を投げる。</p>
     *
     * <p>#125 の原因は、exact 実行の入出力は監査済み exact セルしか通れないのに、
     * 経路が成立しない盤面でも ACO が所有権を取り、理由も出さず永久待機することだった。
     * ACO 側の修正 (境界 route の事前検証) が入った状態では、このプロットの結末は次のとおり。</p>
     * <ul>
     *   <li>完成品の exact 受け皿が無いので、ACO は所有せず外部コンシューマへ委譲する</li>
     *   <li>このグリッドには Quantum CPU も無いので、こちらの
     *       {@code supportsQuantumCpu} の判定が {@code INCOMPLETE_PLAN} で明確に断る</li>
     * </ul>
     *
     * <p>つまり<b>「受けられない注文は黙って止まるのではなく、投入時点で断られる」</b>
     * ことがこのプロットの検査対象。修正前の ACO (素の 1.5.23/1.5.24) では投入が
     * 通ってしまい赤になる。これは #125 が直っていないことを示す正しい赤である。</p>
     *
     * <p>(NeoECO Vector 実行系そのものを完走させる検査は、無限に受けられる
     * exact 受け皿 (ExtendedAE Plus の実セル相当) が要るため、ここでは扱わない。)</p>
     *
     * <p>構造は AAC の {@code AACMultiBlocks} の定義 (= NeoECO L9 設計図の Vector 版) を
     * コントローラ北向きのままプロット座標へ写したもの。形成は AE2 と同じ
     * MBCalculator 方式なので、正しく置けば自動で組み上がる。グリッドへは
     * crafting_interface (形成後に全面が接続可) からケーブルで繋ぐ。</p>
     *
     * <p>ACC が居ない環境、または ACO が exact 実行を持たない (1.5.22 以前) 環境では
     * 何も検査せずに成功する。実行は
     * {@code -PwithAco=true -PacoVersion=1.5.24 -PwithAcc=true}。</p>
     */
    @TestPlot("insaneae_craft_past_long_acc")
    public static void craftPastLongAcc(PlotBuilder plot) {
        var controller = optionalBlock(
                "advanced_assembly_computing", "vector_crafting_controller");
        var worker = optionalBlock(
                "advanced_assembly_computing", "vector_crafting_worker");
        var parallelCore = optionalBlock(
                "advanced_assembly_computing", "vector_crafting_parallel_core");
        var casing = optionalBlock("neoecoae", "crafting_casing");
        var patternBus = optionalBlock("neoecoae", "crafting_pattern_bus");
        var vent = optionalBlock("neoecoae", "crafting_vent");
        var craftingInterface = optionalBlock("neoecoae", "crafting_interface");
        var inputHatch = optionalBlock("neoecoae", "input_hatch");
        var outputHatch = optionalBlock("neoecoae", "output_hatch");
        if (controller == null || worker == null || parallelCore == null || casing == null
                || patternBus == null || vent == null || craftingInterface == null
                || inputHatch == null || outputHatch == null) {
            // 空のプロットは AE2 の Plot#getBounds が通らないので 1 つ置く。
            plot.cable("0 0 0");
            plot.test(helper -> helper.startSequence().thenSucceed());
            return;
        }

        // 要求量。完了判定にも使うので 1 か所にまとめる。
        final long requested = Long.MAX_VALUE;
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("[0,9] 0 0");
        plot.cable("9 0 [1,2]");
        // crafting_interface (8,1,2) の東面へ。
        plot.cable("9 1 2");
        plot.blockEntity("1 0 0", AEBlocks.DRIVE, drive -> {
            drive.getInternalInventory().addItems(ultraCreativeCell(Items.OAK_LOG));
            // 完成品の置き場。craftPastLong と同じく、置き場が先に尽きないよう 8E セル。
            drive.getInternalInventory().addItems(new ItemStack(
                    ModCells.ITEM_CELLS.get(InsaneCraftingUnitType.STORAGE_8E).get()));
        });
        // クラフト CPU も craftPastLong と同じ 5 個 (必要量 3.2e19 bytes に対し余裕を持たせる)。
        plot.blockState("2 [0,1] [0,2]", ModBlocks.BIG_INTEGER_CPU.get().defaultBlockState());
        plot.block("2 1 0", AEBlocks.CRAFTING_ACCELERATOR);
        // 計算・投入の主体 (IActionHost)。パターンは入れない。
        plot.block("3 0 0", AEBlocks.PATTERN_PROVIDER);

        var facing = BlockStateProperties
                .HORIZONTAL_FACING;
        var north = Direction.NORTH;
        var south = Direction.SOUTH;
        // 頭部: いったん全部ケーシングで埋めてから、機能ブロックで上書きする。
        plot.blockState("[6,8] [0,2] [1,2]", casing.defaultBlockState());
        plot.blockState("7 1 1", controller.defaultBlockState().setValue(facing, north));
        plot.blockState("8 0 2", outputHatch.defaultBlockState());
        plot.blockState("8 1 2", craftingInterface.defaultBlockState());
        plot.blockState("8 2 2", inputHatch.defaultBlockState());
        // 作業列 (反復 1 回ぶん)。Worker と並列コアはコントローラと同じ北向き、
        // パターンバスと排熱口は後ろ (南) 向きが形成条件。
        plot.blockState("5 1 1", worker.defaultBlockState().setValue(facing, north));
        plot.blockState("5 0 1", parallelCore.defaultBlockState().setValue(facing, north));
        plot.blockState("5 2 1", parallelCore.defaultBlockState().setValue(facing, north));
        plot.blockState("5 0 2", patternBus.defaultBlockState().setValue(facing, south));
        plot.blockState("5 1 2", vent.defaultBlockState().setValue(facing, south));
        plot.blockState("5 2 2", patternBus.defaultBlockState().setValue(facing, south));
        // 端のふた。
        plot.blockState("4 [0,2] [1,2]", casing.defaultBlockState());

        plot.test(helper -> {
            // ACO の exact 実行 (1.5.23+) と ACC の連携が両方居るときだけ意味がある。
            if (optionalClass("com.syaru.ae2craftingoptimizer.access.ExactCraftingJobAccess") == null
                    || optionalClass("com.syaru.advancedassemblycomputing.integration"
                            + ".AACCraftingTableBatchAdapter") == null) {
                helper.startSequence().thenSucceed();
                return;
            }
            var state = new Object() {
                MachineSource source;
                Future<ICraftingPlan> plan;
                long firstSample;
            };
            var sequence = helper.startSequence();

            // Vector Crafting System が組み上がるまで待つ。
            sequence.thenWaitUntil(() -> {
                var be = helper.getBlockEntity(new BlockPos(7, 1, 1));
                helper.check(be != null && reflectFormed(be),
                        "Vector Crafting System が組み上がらない (構造の並びか、"
                                + "NeoECO/AAC 側の検証条件が変わった可能性)");
            });

            // パターンは NeoECO のパターンバスへ。craftPastLong と同じ 2 段:
            // 原木 -> 板材 -> ボタン。
            sequence.thenExecute(() -> {
                var bus = helper.getBlockEntity(new BlockPos(5, 0, 2));
                helper.check(bus != null, "パターンバスの BlockEntity が無い");
                insertPatternsIntoBus(bus,
                        CraftingPatternHelper.encodeShapelessCraftingRecipe(helper.getLevel(),
                                new ItemStack(Items.OAK_LOG)),
                        CraftingPatternHelper.encodeShapelessCraftingRecipe(helper.getLevel(),
                                new ItemStack(Items.OAK_PLANKS)));
            });
            // ACO の compiled graph と generation が落ち着くまで待つ。
            sequence.thenIdle(60);

            sequence.thenExecute(() -> {
                var grid = helper.getGrid(BlockPos.ZERO);
                state.source = new MachineSource(
                        (IActionHost)
                                helper.getBlockEntity(new BlockPos(3, 0, 0)));
                state.plan = grid.getCraftingService().beginCraftingCalculation(
                        helper.getLevel(), () -> state.source,
                        AEItemKey.of(Items.OAK_BUTTON), requested,
                        CalculationStrategy.REPORT_MISSING_ITEMS);
            });
            sequence.thenWaitUntil(() -> helper.check(state.plan.isDone(), "計算が終わらない"));
            sequence.thenExecute(() -> {
                ICraftingPlan plan;
                try {
                    plan = state.plan.get();
                } catch (Exception failure) {
                    throw new GameTestAssertException("計算が例外で終わった: " + failure);
                }
                helper.check(!plan.simulation(),
                        "計算がシミュレーション止まり (素材不足扱い)。"
                                + "ACO の判断: " + acoPlanDiagnostics());
                var result = helper.getGrid(BlockPos.ZERO).getCraftingService()
                        .submitJob(plan, null, null, false, state.source);
                // <b>断られるのが正解。</b>exact 受け皿も Quantum CPU も無いこの盤面では
                // 誰も実行できない。修正前の ACO はここで受理してしまい、
                // ジョブが理由も出さず永久待機する (= #125)。
                helper.check(!result.successful(),
                        "実行手段の無い long 超の要求を受理してしまった "
                                + "(ACO #125 の無言停止の再現。修正済みの ACO なら"
                                + "委譲とゲートで投入時点で断られる)");
                helper.check(result.errorCode()
                                == CraftingSubmitErrorCode
                                        .INCOMPLETE_PLAN,
                        "断り方が想定と違う: errorCode=" + result.errorCode()
                                + " (INCOMPLETE_PLAN を期待。ACOの判断="
                                + acoPlanDiagnostics() + ")");
            });

            // 断ったあと、CPU がジョブを抱えていないこと (受理→即取消の残骸も無いこと)。
            sequence.thenIdle(10);
            sequence.thenExecute(() -> {
                for (var cpu : helper.getGrid(BlockPos.ZERO).getCraftingService().getCpus()) {
                    helper.check(!cpu.isBusy(),
                            "断ったはずの要求でクラフト CPU が忙しいまま");
                }
                long stored = storedAmount(helper, Items.OAK_BUTTON);
                helper.check(stored == 0,
                        "断ったはずの要求で完成品が湧いている (" + stored + ")");
            });
            sequence.thenSucceed();
        // 多ブロックの形成待ちが加わるぶん、craftPastLong より少し余裕を持たせる。
        }).maxTicks(500);
    }

    /** NeoECO の {@code NEBlockEntity#isFormed} を反射で読む (コンパイル時に型が無い)。 */
    private static boolean reflectFormed(Object blockEntity) {
        try {
            return (Boolean) blockEntity.getClass().getMethod("isFormed").invoke(blockEntity);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return false;
        }
    }

    /**
     * NeoECO のパターンバスへエンコード済みパターンを入れる。
     *
     * <p>{@code ECOCraftingPatternBusBlockEntity.itemHandler} は public final の
     * {@code IItemHandlerModifiable} (表示中ページのスロットへ写像される)。
     * 反射なのはコンパイル時に NeoECO の型が無いため。</p>
     */
    private static void insertPatternsIntoBus(Object bus, ItemStack... patterns) {
        IItemHandlerModifiable handler;
        try {
            handler = (IItemHandlerModifiable)
                    bus.getClass().getField("itemHandler").get(bus);
        } catch (ReflectiveOperationException | ClassCastException failure) {
            throw new GameTestAssertException("パターンバスへ反射でアクセスできない "
                    + "(NeoECO の itemHandler フィールドが変わった可能性): " + failure);
        }
        int slot = 0;
        for (ItemStack pattern : patterns) {
            ItemStack rest = pattern;
            while (!rest.isEmpty() && slot < handler.getSlots()) {
                rest = handler.insertItem(slot++, rest, false);
            }
            if (!rest.isEmpty()) {
                throw new GameTestAssertException(
                        "パターンバスがパターンを受け取らない: " + pattern);
            }
        }
    }

    /**
     * ACO の compiled graph が、その木をどう見ているかを覗く。
     *
     * <p>{@code NO_COMPILED_PROGRAM} は「{@code CompiledRootProgram} が組めなかった」としか
     * 言わないので、組めない条件 (パターンが 1 つでない / 循環 / 不完全) のどれなのかを
     * ここで直接聞く。全部 ACO の public メソッドだが、ACO 無しでも読めるよう反射で呼ぶ。</p>
     */
    private static String acoGraphProbe(PlotTestHelper helper,
            ItemLike... keys) {
        try {
            Class<?> cache = Class.forName(
                    "com.syaru.ae2craftingoptimizer.engine.Ae2CompiledCraftingGraphCache",
                    false, LongOverflowCraftPlots.class.getClassLoader());
            Object snapshot = cache.getMethod("getOrCompile",
                            IGrid.class, Level.class)
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
            report.append("craftables=").append(((Set<?>) craftables.invoke(snapshot)).size());
            for (var item : keys) {
                AEKey key = AEItemKey.of(item.asItem());
                report.append(" | ").append(item.asItem())
                        .append(": patterns=").append(count.invoke(snapshot, key))
                        .append(" oneFullyCompiled=").append(oneFull.invoke(snapshot, key))
                        .append(" incomplete=").append(incomplete.invoke(snapshot, key))
                        .append(" cyclic=").append(cyclic.invoke(graph, key))
                        .append(" rootProgram=")
                        .append(((Optional<?>) root.invoke(snapshot, key)).isPresent());
            }
            return report.toString();
        } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) {
            return "(ACO の compiled graph を覗けない: " + unavailable + ")";
        }
    }

    /**
     * @param configureOutputToo クリエイティブセルに完成品 (板材) も設定するか
     */
    private static void craftCompletionPlot(PlotBuilder plot, boolean configureOutputToo) {
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("[0,3] 0 0");
        plot.blockEntity("1 0 0", AEBlocks.DRIVE, drive -> {
            // 材料は超強化クリエイティブセルから。完成品の置き場に普通のセルを 1 枚。
            drive.getInternalInventory().addItems(configureOutputToo
                    ? ultraCreativeCell(Items.OAK_LOG, Items.OAK_PLANKS)
                    : ultraCreativeCell(Items.OAK_LOG));
            drive.getInternalInventory().addItems(AEItems.ITEM_CELL_64K.stack());
        });
        plot.block("2 0 0", AEBlocks.CRAFTING_STORAGE_64K);
        plot.block("2 1 0", AEBlocks.CRAFTING_ACCELERATOR);
        plot.blockState("3 0 0", ModBlocks.QUANTUM_CPU.get().defaultBlockState());

        // 少量にしてあるのは「速いか」ではなく「終わるか」を見るテストだから。
        final long requested = 64;

        plot.test(helper -> {
            var state = new Object() {
                TestCraftingJob job;
            };
            var sequence = helper.startSequence();

            sequence.thenExecute(() -> {
                var cpu = (QuantumCpuBlockEntity) helper.getBlockEntity(new BlockPos(3, 0, 0));
                cpu.getLogic().getPatternInv().addItems(
                        CraftingPatternHelper.encodeShapelessCraftingRecipe(helper.getLevel(),
                                new ItemStack(Items.OAK_LOG)));
            });
            sequence.thenIdle(5);

            sequence.thenExecute(() -> state.job = new TestCraftingJob(
                    helper, BlockPos.ZERO, AEItemKey.of(Items.OAK_PLANKS), requested));
            sequence.thenWaitUntil(() -> state.job.tickUntilStarted());

            // 完成待ちが減って<b>要求量がまるごとネットワークに載る</b>まで待つ。
            // 途中で消えていると、ここで時間切れになって落ちる (= 症状の再現)。
            sequence.thenWaitUntil(() -> {
                long stored = storedAmount(helper, Items.OAK_PLANKS);
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
}
