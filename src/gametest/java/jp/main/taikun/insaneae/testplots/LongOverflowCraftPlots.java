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
import appeng.server.testworld.PlotBuilder;
import appeng.server.testworld.PlotTestHelper;
import appeng.server.testworld.TestCraftingJob;
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
//? if >=1.21 {
import appeng.server.testplots.TestPlotClass;
import jp.main.taikun.insaneae.crafting.IBigCraftingCapacity;
//?}
//? if <1.21 {
/*import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.CraftingRecipe;
*///?}

/**
 * クリエイティブセルからのクラフトと、longを超える量のクラフトを完走できるか。
 *
 * <p>走らせ方は {@link TestPlotSupport} を参照。</p>
 */
//? if >=1.21 {
@TestPlotClass
//?}
public final class LongOverflowCraftPlots {

    private LongOverflowCraftPlots() {
    //? if >=1.21 {
    }

    /**
     * ACOのBigInteger計画が持つ正確な必要バイト数。
     *
     * <p>{@code plan.bytes()} はlongに飽和するので、断られた理由が
     * 「BigInteger計画が作れなかった」のか「作れたが容量が足りない」のかを
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
    //?}
    }

    /**
     * クリエイティブセルから材料を供給したクラフトが、最後まで終わるか。
     *
     * <p>「クラフトは進行中のままタスクが空になり、いつまでも完了しない」という症状を
     * 追うためのテスト。クリエイティブセルは、強化・超強化・AE2本家とも同じで、次のとおり。</p>
     *
     * <pre>
     * insert(what, amount)        → 設定済みの種類は無限に飲み込む。実質ボイド
     * isPreferredStorageFor(what) → 設定済みの種類は優先搬入先を名乗る
     * </pre>
     *
     * <p>なので、クラフトの完成品がセルに設定されていると、完成品がクラフトCPUの
     * 完成待ちに返る前に吸い込まれて消えるおそれがある。そうなると完成待ちが永遠に
     * 減らず、タスクだけ空になってジョブが終わらない。</p>
     *
     * <p>ここでは2つの並びを両方とも「完了すること」で検査する:</p>
     * <ol>
     *   <li>セルには材料だけ — 想定どおりの使い方</li>
     *   <li>セルに材料と完成品の両方 — 上の懸念そのままの並び</li>
     * </ol>
     *
     * <p>2が落ちるならセルが完成品を飲んでいる。1が落ちるなら供給側の問題。
     * どちらも通るなら、完了しない原因はここではない。</p>
     */
    @TestPlot("insaneae_craft_from_creative_cell")
    public static void craftFromCreativeCell(PlotBuilder plot) {
        craftCompletionPlot(plot, false);
    }

    /** {@link #craftFromCreativeCell} の並び2 — 完成品もセルに設定してある場合。 */
    @TestPlot("insaneae_craft_output_also_in_cell")
    public static void craftOutputAlsoInCell(PlotBuilder plot) {
        craftCompletionPlot(plot, true);
    }

    /**
     * 同じアイテムを申告するセルが2枚あっても、在庫が負数へ折り返さないこと。
     *
     * <p>{@code KeyCounter} はキーごとにlong。クリエイティブセルは設定した種類を
     * {@code Long.MAX_VALUE} で申告するので、素朴に足すと2枚目で折り返す。
     * 負の在庫を見たACOは「正確値を復元できない」として計画ごと降りるため
     * ({@code WidePlanUnavailableException: BigInteger inventory sidecar is incomplete})、
     * セルを1枚足しただけであらゆるクラフトが失敗する。実機では
     * ExtendedAE PlusのInfinityセルと同居させたときに発生した。</p>
     */
    @TestPlot("insaneae_creative_cell_no_overflow")
    public static void creativeCellNoOverflow(PlotBuilder plot) {
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("[0,1] 0 0");
        plot.blockEntity("1 0 0", AEBlocks.DRIVE, drive -> {
            // 同じ種類を申告するセルを2枚。実機の「無限セル2枚」を最小構成で再現する
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
     * 中間素材の必要数がlongを超える木を、最後まで作り切れること。
     *
     * <p>{@link #craftPastLong} との違いはどこがlongを超えるか。あちらは
     * 完成品の要求数だけが大きく、木を下りるほど数が減る。ボタン、板、原木の順。
     * こちらは途中の段が要求数より多くなる。チェスト1個は板8枚なので、
     * 完成品がlong内でも中間素材がlongを超える。</p>
     *
     * <p>実機で8倍ずつ100段重ねた鎖を頼んだとき、中間段がlongを超える
     * ちょうどそこから完成しなくなる症状が出た。22段までは完走し、23段から停止した。
     * 既存のテストはどれも中間段がlong内なので、この形だけ穴になっていた。</p>
     */
    @TestPlot("insaneae_craft_past_long_intermediate")
    public static void craftPastLongIntermediate(PlotBuilder plot) {
        // チェスト1個 = 板8枚、原木1本 = 板4枚
        // 要求5e18だと 板 = 4e19、板パターンの実行回数 = 1e19で、
        // どちらもLong.MAX_VALUE (9.22e18) を超える
        // 回数がlong内だとACOは通常のlong計画を返し、BigInteger実行経路を
        // 通らないと、このテストが何も検査しないことになるので、ここは回数で選ぶ
        final long requested = 5_000_000_000_000_000_000L;
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("[0,3] 0 0");
        plot.blockEntity("1 0 0", AEBlocks.DRIVE, drive -> {
            drive.getInternalInventory().addItems(ultraCreativeCell(Items.OAK_LOG));
            // 完成品の置き場。8Eセルなら3.6e19個入るので要求数で先に満杯にならない
            drive.getInternalInventory().addItems(new ItemStack(
                    ModCells.ITEM_CELLS.get(InsaneCraftingUnitType.STORAGE_8E).get()));
        });
        plot.blockState("2 [0,1] [0,2]", ModBlocks.BIG_INTEGER_CPU.get().defaultBlockState());
        plot.block("2 1 0", AEBlocks.CRAFTING_ACCELERATOR);
        plot.blockState("3 0 0", ModBlocks.QUANTUM_CPU.get().defaultBlockState());

        plot.test(helper -> {
            // ACO無しではこの規模の計画自体が作れないので、何も検査しない
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
                // 板8 → チェスト1。中央だけ空けた3x3のバニラレシピ
                //? if >=1.21 {
                Object[] chest = new Object[] {
                        Items.OAK_PLANKS, Items.OAK_PLANKS, Items.OAK_PLANKS,
                        Items.OAK_PLANKS, null, Items.OAK_PLANKS,
                        Items.OAK_PLANKS, Items.OAK_PLANKS, Items.OAK_PLANKS};
                //?} else {
                /*ItemStack[] sparse = new ItemStack[9];
                for (int slot = 0; slot < sparse.length; slot++) {
                    sparse[slot] = slot == 4 ?
                            ItemStack.EMPTY
                            : new ItemStack(Items.OAK_PLANKS);
                }
                var chestRecipe = helper.getLevel().getRecipeManager()
                        .byKey(ResourceLocation.fromNamespaceAndPath("minecraft", "chest"))
                        .orElseThrow(() -> new GameTestAssertException("チェストのレシピが無い"));
                *///?}
                cpu.getLogic().getPatternInv().addItems(
                        //? if >=1.21 {
                        CraftingPatternHelper.encodeCraftingPattern(
                                helper.getLevel(), chest, false, false));
                        //?} else {
                        /*PatternDetailsHelper.encodeCraftingPattern(
                                (CraftingRecipe) chestRecipe,
                                sparse, new ItemStack(Items.CHEST), false, false));
                        *///?}
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

            // tick数まで見る。加速カード満載 + タスク統合なら、この規模は
            // 数tickで終わるのが仕様。窓は1 tickに1024枚使えるため、段ごとに
            // tickを消費する実装だと4 tickあたり1段しか進まず、この待ち時間では終わらない
            sequence.thenIdle(2);
            sequence.thenExecute(() ->
                    state.firstSample = storedAmount(helper, Items.CHEST));
            sequence.thenIdle(6);
            sequence.thenExecute(() -> {
                long second = storedAmount(helper, Items.CHEST);
                helper.check(second >= state.firstSample,
                        "完成品が減っている (" + state.firstSample + " → " + second + ")");
                // 「増えている」だけでは不十分。実機の症状は「進むが終わらない」なので、
                // この規模なら数tickで作り切れるはずの完走を要求する
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
     * longを超える要求がBigInteger経路で実際に走り出すか。
     *
     * <p>{@link #craftFromCreativeCell} はlongに収まる規模なので、AE2本来の経路しか
     * 通らない。報告されている症状はそこを超えた規模でだけ出るので、
     * こちらは要求を {@code Long.MAX_VALUE} にしてACOのexact経路を必ず踏ませる。</p>
     *
     * <p>2段のツリーになっている。ボタン &lt;- 板材 &lt;- 原木 の順なので、パターン実行回数の合計は
     * 要求量の1.25倍ほどになり、合計がlongに収まらない。
     * ACOはここで {@code hasAggregatePastLong()} を見てwide planへ切り替える。</p>
     *
     * <h2>何を検査するか</h2>
     * <p>この規模は完了しなくて当たり前なので、完了は見ない。見るのは</p>
     * <ol>
     *   <li>投入が通ること。「failed to submit job」にならない</li>
     *   <li>実際に完成品が増え続けること — 「進行中のまま何も起きない」を捕まえる</li>
     * </ol>
     *
     * <p>クラフトCPUはInsaneAEのBigIntegerクラフトCPUで、理論上限容量を持つ。
     * 普通のクラフトストレージだと、この規模は容量不足で投入前に弾かれてしまう。</p>
     *
     * <h2>ACO込みで落ちるとき</h2>
     * <p>ACOの版によっては、投入が {@code CPU_TOO_SMALL} で断られて落ちる。容量の問題ではなく、
     * 必要bytesもCPUの空きも同じ {@code Long.MAX_VALUE} で、AE2自身の判定
     * ({@code available >= bytes}) は通っている。断っているのはACOの
     * {@code CraftingCpuClusterBigCapacityGuardMixin} で、wide planにBigIntegerの裏付けが無いときに
     * {@code CPU_TOO_SMALL} を返す。ACOの診断には理由が {@code NO_COMPILED_PROGRAM} と出る。</p>
     *
     * <p>パターンの種類をクラフトから加工に変えても同じ理由で断られる。加工版は{@link #craftPastLongProcessing}で扱う。
     * {@link #acoGraphProbe} でACOのcompiled graphを直接読むと木は健全で ({@code rootProgram=true})、
     * 同じ {@code rootProgram(what)} の呼び出しもこちらでは値を返す。ACOの判断とACO自身のgraphが
     * 食い違っているので、原因はACO側にあり、InsaneAEのパターンや在庫の出し方ではない。</p>
     *
     * <p>注意: ACOを載せるには {@code -PwithAco=true} と書くこと。
     * {@code -PwithAco} だけだと値が空文字になり、build.gradleの {@code == 'true'} が
     * 偽になり、エラーなしでACO無しのまま走る。テストは通ってしまう。</p>
     */
    @TestPlot("insaneae_craft_past_long")
    public static void craftPastLong(PlotBuilder plot) {
        // 要求量。完了判定にも使うので1か所にまとめる
        final long requested = Long.MAX_VALUE;
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("[0,3] 0 0");
        plot.blockEntity("1 0 0", AEBlocks.DRIVE, drive -> {
            drive.getInternalInventory().addItems(ultraCreativeCell(Items.OAK_LOG));
            // 完成品の置き場。64Kセルだと1種類で520,192個 ((65536-512)*8) で満杯になり、
            // クラフトが「途中で止まった」ようにしか見えない。8Eセルなら3.6e19個入るので
            // Long.MAX_VALUEの注文でも置き場が先に尽きない
            drive.getInternalInventory().addItems(new ItemStack(
                    ModCells.ITEM_CELLS.get(InsaneCraftingUnitType.STORAGE_8E).get()));
        });
        // 理論上限容量のBigIntegerクラフトCPU。普通のクラフトストレージだと
        // この規模は「容量が足りない」で投入前に弾かれる
        // 1個では足りない。この注文の正確な必要量は3.2e19 bytesで、
        // このブロック1個の容量 (2^63-1 = 9.2e18) の約3.5倍ある
        // 足りないまま投入するとCPU_TOO_SMALLで断られ、
        // ACO側の不具合と見分けが付かないので、余裕を持って5個積む
        plot.blockState("2 [0,1] [0,2]", ModBlocks.BIG_INTEGER_CPU.get().defaultBlockState());
        plot.block("2 1 0", AEBlocks.CRAFTING_ACCELERATOR);
        plot.blockState("3 0 0", ModBlocks.QUANTUM_CPU.get().defaultBlockState());

        plot.test(helper -> {
            // ACOが無ければ何も検査しない
            // この2本は「ACOがlongを超えるBigInteger計画を作れる」ことが前提で、
            // ACO無しではAE2が素直に計画を作れず、失敗しても意味が読めない
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
            // ACOのcompiled graphとgenerationが落ち着くまで待つ
            sequence.thenIdle(60);

            // TestCraftingJobは失敗理由を「failed to submit job」としか言わないので、
            // ここは自分で計算 → 投入して断られた理由を出す
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
                    //? if >=1.21 {
                            .append(" busy=").append(cpu.isBusy());
                    // longに飽和したavailableだけでは足りるか分からないので、
                    // 正確な容量も並べる。こちらのBigInteger会計による値
                    if (cpu instanceof IBigCraftingCapacity exact) {
                        cpuSizes.append(" exactCapacity=").append(exact.insaneae$exactStorageCapacity());
                    }
                    cpuSizes.append(']');
                    //?} else {
                            /*.append(" busy=").append(cpu.isBusy()).append(']');
                    *///?}
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

            // 進んでいるか、または既に終わっているか
            //
            // 「増えていること」だけを見てはいけない。Quantum CPUはこの規模を
            // 数tickで作りきってしまうので、最初の標本が既に要求量に達していることがある
            // そのとき「40 tick経っても増えない」は停止ではなく完了である
            // 置き場が満杯でも増えなくなる。完成品は8Eセルに入れてあるので、
            // ここで頭打ちになるなら本当に作り終えたとき
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
        // 待ち時間の合計、60 + 計算 + 20 + 40 tickを賄う
        }).maxTicks(400);
    }

    /**
     * longを超える計画を「加工パターン」で頼んだときは、はっきり断ること。
     *
     * <p>{@link #craftPastLong} と同じ規模・同じ木を、加工パターンで組んだもの。
     * ただし期待する結果は逆で、こちらは投入が
     * {@code INCOMPLETE_PLAN} で断られるのが正解。</p>
     *
     * <p>理由は {@code CraftingCpuLogicMixin} にある。ACOのBigInteger計画を
     * 実際に回せるのはこちらのQuantum CPUだけで、Quantum CPUが扱えるのは
     * クラフトテーブル用パターンだけ ({@code IMolecularAssemblerSupportedPattern})。
     * 加工パターンが混じった計画をそのまま受けると、飽和したlongのタスクが
     * 永久に終わらないジョブになってCPUを占有する。
     * だから受理せず、AE2の明示的な失敗として返している。</p>
     *
     * <p>このテストが緑 = 「危ないものを黙って受けない」が守れているということ。
     * 逆にここが投入成功に変わったら、リグレッションを疑うこと。</p>
     */
    @TestPlot("insaneae_craft_past_long_processing")
    public static void craftPastLongProcessing(PlotBuilder plot) {
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("[0,3] 0 0");
        plot.blockEntity("1 0 0", AEBlocks.DRIVE, drive -> {
            drive.getInternalInventory().addItems(ultraCreativeCell(Items.OAK_LOG));
            // 完成品の置き場。64Kセルだと1種類で520,192個 ((65536-512)*8) で満杯になり、
            // クラフトが「途中で止まった」ようにしか見えない。8Eセルなら3.6e19個入るので
            // Long.MAX_VALUEの注文でも置き場が先に尽きない
            drive.getInternalInventory().addItems(new ItemStack(
                    ModCells.ITEM_CELLS.get(InsaneCraftingUnitType.STORAGE_8E).get()));
        });
        // 1個では足りない。この注文の正確な必要量は3.2e19 bytesで、
        // このブロック1個の容量 (2^63-1 = 9.2e18) の約3.5倍ある
        // 足りないまま投入するとCPU_TOO_SMALLで断られ、
        // ACO側の不具合と見分けが付かないので、余裕を持って5個積む
        plot.blockState("2 [0,1] [0,2]", ModBlocks.BIG_INTEGER_CPU.get().defaultBlockState());
        plot.block("2 1 0", AEBlocks.CRAFTING_ACCELERATOR);
        plot.block("3 0 0", AEBlocks.PATTERN_PROVIDER);

        plot.test(helper -> {
            // ACOが無ければ何も検査しない
            // この2本は「ACOがlongを超えるBigInteger計画を作れる」ことが前提で、
            // ACO無しではAE2が素直に計画を作れず、失敗しても意味が読めない
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
                // craftPastLongと同じ2段: 原木 ->板材 ->ボタン
                patterns.addItems(processingPattern(Items.OAK_LOG, 1, Items.OAK_PLANKS, 4));
                patterns.addItems(processingPattern(Items.OAK_PLANKS, 1, Items.OAK_BUTTON, 1));
            });
            // ACOのcompiled graphとgenerationが落ち着くまで待つ
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
                        //? if >=1.21 {
                        "加工パターンの BigInteger 計画を受理してしまった。"
                                + "Quantum CPU は加工パターンを回せないので、"
                                + "受けると終わらないジョブが CPU を占有する");
                        //?} else {
                        /*"加工パターンのBigInteger計画を受理してしまった "
                                + "(受けると終わらないジョブが CPU を占有する)");
                        *///?}
                helper.check(result.errorCode()
                                == CraftingSubmitErrorCode
                                        .INCOMPLETE_PLAN,
                        "断り方が想定と違う: errorCode=" + result.errorCode()
                                + " (INCOMPLETE_PLAN を期待。"
                                + "正確な必要bytes=" + exactPlanBytes(plan) + ")");
            });
            sequence.thenSucceed();
        // 既定の制限時間だと足りない。待ち60 tickと計算の時間を賄う
        }).maxTicks(400);
    //? if <1.21 {
    /*}

    /^*
     * ACOのBigInteger計画が持つ正確な必要バイト数。
     *
     * <p>{@code plan.bytes()} はlongに飽和するので、断られた理由が
     * 「BigInteger計画が作れなかった」のか「作れたが容量が足りない」のかを
     * 区別できない。ここが {@code <BigInteger計画なし>} なら前者。</p>
     ^/
    private static String exactPlanBytes(ICraftingPlan plan) {
        try {
            return AcoBigIntegerPlanBridge.inspect(plan)
                    .map(exact -> exact.exactBytes().toString())
                    .orElse("<BigInteger計画なし>");
        } catch (RuntimeException | LinkageError unavailable) {
            return "<読めず: " + unavailable + ">";
        }
    *///?}
    }

    /**
     * 同じlong超の注文を、ACC (Advanced Assembly Computing) のVector実行系で走らせる対照実験。
     *
     * <p>{@link #craftPastLong}の実行側はうちのQuantum CPUで、汎用の
     * {@code CraftingTableBatchTarget}を名乗る。一方、ACOの物理実行
     * ({@code PhysicalCraftingTreeTransaction}) の待ち理由の文字列はほとんどがNeoECOを
     * 名指ししており、本来想定されている実行系はACC + NeoECOのVector Crafting
     * Systemと見ている。build.gradleのwithAccの説明を参照。そこでこちらは
     * Quantum CPUを置かず、パターンをNeoECOのパターンバスへ入れて同じ注文を投げる。</p>
     *
     * <p>#125の原因は、exact実行の入出力は監査済みexactセルしか通れないのに、
     * 経路が成立しない盤面でもACOが所有権を取り、理由も出さず永久待機することだった。
     * ACO側で境界routeの事前検証が入った状態では、このプロットの結末は次のとおり。</p>
     * <ul>
     *   <li>完成品のexact受け皿が無いので、ACOは所有せず外部コンシューマへ委譲する</li>
     *   <li>このグリッドにはQuantum CPUも無いので、こちらの
     *       {@code supportsQuantumCpu} の判定が {@code INCOMPLETE_PLAN} で明確に断る</li>
     * </ul>
     *
     * <p>つまり「受けられない注文は黙って止まるのではなく、投入時点で断られる」
     * ことがこのプロットの検査対象。修正前の素の1.5.23/1.5.24のACOでは投入が
     * 通ってしまい赤になる。これは #125が直っていないことを示す正しい赤である。</p>
     *
     * <p>NeoECO Vector実行系そのものを完走させる検査は、無限に受けられる
     * exact受け皿として、ExtendedAE Plusの実セルに相当するものが要るため、ここでは扱わない。</p>
     *
     * <p>構造はAACの {@code AACMultiBlocks} の定義を、NeoECO L9設計図のVector版から取った。
     * コントローラ北向きのままプロット座標へ写したもの。形成はAE2と同じ
     * MBCalculator方式なので、正しく置けば自動で組み上がる。グリッドへは
     * crafting_interfaceからケーブルで繋ぐ。形成後は全面が接続可になる。</p>
     *
     * <p>ACCが居ない環境、またはACOがexact実行を持たない1.5.22以前の環境では
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
            // 空のプロットはAE2のPlot#getBoundsが通らないので1つ置く
            plot.cable("0 0 0");
            plot.test(helper -> helper.startSequence().thenSucceed());
            return;
        }

        // 要求量。完了判定にも使うので1か所にまとめる
        final long requested = Long.MAX_VALUE;
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("[0,9] 0 0");
        plot.cable("9 0 [1,2]");
        // crafting_interface (8,1,2) の東面へ
        plot.cable("9 1 2");
        plot.blockEntity("1 0 0", AEBlocks.DRIVE, drive -> {
            drive.getInternalInventory().addItems(ultraCreativeCell(Items.OAK_LOG));
            // 完成品の置き場。craftPastLongと同じく、置き場が先に尽きないよう8Eセル
            drive.getInternalInventory().addItems(new ItemStack(
                    ModCells.ITEM_CELLS.get(InsaneCraftingUnitType.STORAGE_8E).get()));
        });
        // クラフトCPUもcraftPastLongと同じ5個。必要量3.2e19 bytesに対し、余裕を持たせる
        plot.blockState("2 [0,1] [0,2]", ModBlocks.BIG_INTEGER_CPU.get().defaultBlockState());
        plot.block("2 1 0", AEBlocks.CRAFTING_ACCELERATOR);
        // 計算・投入の主体 (IActionHost)。パターンは入れない
        plot.block("3 0 0", AEBlocks.PATTERN_PROVIDER);

        var facing = BlockStateProperties
                .HORIZONTAL_FACING;
        var north = Direction.NORTH;
        var south = Direction.SOUTH;
        // 頭部: いったん全部ケーシングで埋めてから、機能ブロックで上書きする
        plot.blockState("[6,8] [0,2] [1,2]", casing.defaultBlockState());
        plot.blockState("7 1 1", controller.defaultBlockState().setValue(facing, north));
        plot.blockState("8 0 2", outputHatch.defaultBlockState());
        plot.blockState("8 1 2", craftingInterface.defaultBlockState());
        plot.blockState("8 2 2", inputHatch.defaultBlockState());
        // 作業列は反復1回ぶん。Workerと並列コアはコントローラと同じ北向き、
        // パターンバスと排熱口は後ろ、南向きが形成条件
        plot.blockState("5 1 1", worker.defaultBlockState().setValue(facing, north));
        plot.blockState("5 0 1", parallelCore.defaultBlockState().setValue(facing, north));
        plot.blockState("5 2 1", parallelCore.defaultBlockState().setValue(facing, north));
        plot.blockState("5 0 2", patternBus.defaultBlockState().setValue(facing, south));
        plot.blockState("5 1 2", vent.defaultBlockState().setValue(facing, south));
        plot.blockState("5 2 2", patternBus.defaultBlockState().setValue(facing, south));
        // 端のふた
        plot.blockState("4 [0,2] [1,2]", casing.defaultBlockState());

        plot.test(helper -> {
            // ACOのexact実行 (1.5.23+) とACCの連携が両方居るときだけ意味がある
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

            // Vector Crafting Systemが組み上がるまで待つ
            sequence.thenWaitUntil(() -> {
                var be = helper.getBlockEntity(new BlockPos(7, 1, 1));
                helper.check(be != null && reflectFormed(be),
                        "Vector Crafting System が組み上がらない (構造の並びか、"
                                + "NeoECO/AAC 側の検証条件が変わった可能性)");
            });

            // パターンはNeoECOのパターンバスへ。craftPastLongと同じ2段:
            // 原木 ->板材 ->ボタン
            sequence.thenExecute(() -> {
                var bus = helper.getBlockEntity(new BlockPos(5, 0, 2));
                helper.check(bus != null, "パターンバスの BlockEntity が無い");
                insertPatternsIntoBus(bus,
                        CraftingPatternHelper.encodeShapelessCraftingRecipe(helper.getLevel(),
                                new ItemStack(Items.OAK_LOG)),
                        CraftingPatternHelper.encodeShapelessCraftingRecipe(helper.getLevel(),
                                new ItemStack(Items.OAK_PLANKS)));
            });
            // ACOのcompiled graphとgenerationが落ち着くまで待つ
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
                // 断られるのが正解。exact受け皿もQuantum CPUも無いこの盤面では
                // 誰も実行できない。修正前のACOはここで受理してしまい、
                // ジョブが理由も出さず永久待機する (= #125)
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

            // 断ったあと、CPUがジョブを抱えていないこと。受理して即取り消した残骸も無いこと
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
        // 多ブロックの形成待ちが加わるぶん、craftPastLongより少し余裕を持たせる
        }).maxTicks(500);
    }

    /** NeoECOの{@code NEBlockEntity#isFormed}を反射で読む。コンパイル時に型が無いため。 */
    private static boolean reflectFormed(Object blockEntity) {
        try {
            return (Boolean) blockEntity.getClass().getMethod("isFormed").invoke(blockEntity);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return false;
        }
    }

    /**
     * NeoECOのパターンバスへエンコード済みパターンを入れる。
     *
     * <p>{@code ECOCraftingPatternBusBlockEntity.itemHandler} はpublic finalの
     * {@code IItemHandlerModifiable}。表示中ページのスロットへ写像される。
     * 反射なのはコンパイル時にNeoECOの型が無いため。</p>
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
     * ACOのcompiled graphが、その木をどう見ているかを覗く。
     *
     * <p>{@code NO_COMPILED_PROGRAM} は「{@code CompiledRootProgram} が組めなかった」としか
     * 言わないので、組めない条件のうち、パターンが1つでない、循環、不完全のどれなのかを
     * ここで直接聞く。全部ACOのpublicメソッドだが、ACO無しでも読めるよう反射で呼ぶ。</p>
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
     * @param configureOutputTooクリエイティブセルに、完成品の板材も設定するか
     */
    private static void craftCompletionPlot(PlotBuilder plot, boolean configureOutputToo) {
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("[0,3] 0 0");
        plot.blockEntity("1 0 0", AEBlocks.DRIVE, drive -> {
            // 材料は超強化クリエイティブセルから。完成品の置き場に普通のセルを1枚
            drive.getInternalInventory().addItems(configureOutputToo ?
                    ultraCreativeCell(Items.OAK_LOG, Items.OAK_PLANKS)
                    : ultraCreativeCell(Items.OAK_LOG));
            drive.getInternalInventory().addItems(AEItems.ITEM_CELL_64K.stack());
        });
        plot.block("2 0 0", AEBlocks.CRAFTING_STORAGE_64K);
        plot.block("2 1 0", AEBlocks.CRAFTING_ACCELERATOR);
        plot.blockState("3 0 0", ModBlocks.QUANTUM_CPU.get().defaultBlockState());

        // 少量にしてあるのは「速いか」ではなく「終わるか」を見るテストだから
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

            // 完成待ちが減って要求量がまるごとネットワークに載るまで待つ
            // 途中で消えていると、ここで時間切れになって落ちる。症状の再現になる
            sequence.thenWaitUntil(() -> {
                long stored = storedAmount(helper, Items.OAK_PLANKS);
                if (stored < requested) {
                    throw new GameTestAssertException("板材が " + stored + "/" + requested
                            + " しかネットワークに無い"
                            + (configureOutputToo ?
                                    " (完成品もクリエイティブセルに設定してある並び"
                                            + " — セルが完成品を飲んでいる疑い)"
                                    : ""));
                }
            });

            // クラフトCPUが仕事を抱えたままになっていないこと
            // タスクだけ空になって「進行中」のまま止まる症状は、ここで捕まる
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
