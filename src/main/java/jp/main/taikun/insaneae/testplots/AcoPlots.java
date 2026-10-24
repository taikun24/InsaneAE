package jp.main.taikun.insaneae.testplots;

import static jp.main.taikun.insaneae.testplots.TestPlotSupport.acoPlanDiagnostics;
import static jp.main.taikun.insaneae.testplots.TestPlotSupport.optionalClass;
import static jp.main.taikun.insaneae.testplots.TestPlotSupport.ultraCreativeCell;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionHost;
import appeng.api.stacks.AEItemKey;
import appeng.core.definitions.AEBlocks;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.helpers.MachineSource;
import appeng.server.testplots.CraftingPatternHelper;
import appeng.server.testplots.TestPlot;
import appeng.server.testworld.PlotBuilder;
import jp.main.taikun.insaneae.cell.InsaneUltraCreativeCellInventory;
import jp.main.taikun.insaneae.crafting.IBigCraftingCapacity;
import jp.main.taikun.insaneae.crafting.InsaneCraftingUnitType;
import jp.main.taikun.insaneae.integration.aco.AcoBigIntegerLimitBridge;
import jp.main.taikun.insaneae.integration.aco.AcoClassNames;
import jp.main.taikun.insaneae.integration.aco.AcoExactJobOwnership;
import jp.main.taikun.insaneae.integration.aco.AcoExactLimits;
import jp.main.taikun.insaneae.mixin.CraftingCpuLogicJobAccessor;
import jp.main.taikun.insaneae.quantum.QuantumCpuBlockEntity;
import jp.main.taikun.insaneae.registries.ModBlocks;
import jp.main.taikun.insaneae.registries.ModCells;
import jp.main.taikun.insaneae.registries.ModUpgrades;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;

import java.math.BigInteger;
import java.util.concurrent.Future;

/**
 * Advanced Crafting Optimization (ACO) との BigInteger 連携。
 *
 * <p>走らせ方は {@link TestPlotSupport} を参照。</p>
 */
public final class AcoPlots {

    private AcoPlots() {
    }

    /**
     * ACO が居るとき、Quantum CPU が<b>正確な BigInteger 実行のターゲットとして見える</b>ことを確かめる。
     *
     * <p>ACO は「{@code ICraftingProvider} が {@code ProviderOwnedPatternBatchTarget} で、
     * 返した BlockEntity が {@code CraftingTableBatchTarget}」という形でターゲットを探す。
     * <b>どちらか片方でも欠けると候補にすら入らず、エラーも出ずに別の経路へ回る</b>ので、
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

            // 超強化クリエイティブセルの BigInteger 在庫。ACO 内部の access に乗っているので、
            // 向こうの名前が変わると<b>エラーなしで効かなくなる</b> — ここで気付けるようにする。
            Class<?> exactStorage = optionalClass("com.syaru.ae2craftingoptimizer.access"
                    + ".ExtendedAePlusBigIntegerCellInventoryAccess");
            helper.check(exactStorage != null,
                    "ACO の ExtendedAePlusBigIntegerCellInventoryAccess が無い "
                            + "(公開フックへ移行できるかもしれない → ACO #101)");
            var cell = new InsaneUltraCreativeCellInventory(
                    new ItemStack(ModCells.ULTRA_CREATIVE_CELL.get()));
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
            int advertised = InsaneUltraCreativeCellInventory
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
     * ACO 1.5.23 以降、exact ジョブの実行を ACO が所有していることを<b>こちらが認識できる</b>か。
     *
     * <p>{@code executeCrafting} の @HEAD にはこちらのまとめ処理と ACO の打ち切りが両方注入されて
     * いて、実機の適用順ではこちらが先に走る。ACO の所有を見落とすと、ACO の台帳に無い実行を
     * 1 回進めてしまう。ここで検査するのは<b>判定そのもの</b> — ACO が生やすメソッド名
     * ({@code aco$isExactJob}) は相手の都合で変わりうるのに、変わっても<b>エラーにならず
     * false が返るだけ</b>になるため、注入の有無ではなく実ジョブに対する戻り値で見張る。</p>
     *
     * <p>ACO が古い (1.5.22 以下) 環境では共通契約のクラスが無いので何も検査しない。</p>
     */
    @TestPlot("insaneae_aco_exact_ownership")
    public static void acoExactOwnership(PlotBuilder plot) {
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("[0,3] 0 0");
        plot.blockEntity("1 0 0", AEBlocks.DRIVE, drive -> {
            // 完成品 (ボタン) もセルに設定してある。ACO の exact 実行は境界の入出力を
            // exact 書き込み可能セルでしか行えず、#125 の修正後は「最終出力の納品先が
            // 無い計画」は所有権を取らずに外部コンシューマへ委譲するようになった。
            // このテストの主題は<b>所有の認識</b>なので、所有が成立する盤面にしておく。
            drive.getInternalInventory().addItems(
                    ultraCreativeCell(Items.OAK_LOG, Items.OAK_BUTTON));
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
                MachineSource source;
                Future<ICraftingPlan> plan;
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
                state.source = new MachineSource(
                        (IActionHost)
                                helper.getBlockEntity(new BlockPos(3, 0, 0)));
                state.plan = helper.getGrid(BlockPos.ZERO).getCraftingService()
                        .beginCraftingCalculation(helper.getLevel(), () -> state.source,
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
                helper.check(!plan.simulation(), "計算がシミュレーション止まり (素材不足扱い)");
                var result = helper.getGrid(BlockPos.ZERO).getCraftingService()
                        .submitJob(plan, null, null, false, state.source);
                helper.check(result.successful(),
                        "long を超える要求の投入が断られた: errorCode=" + result.errorCode());
            });

            // 投入直後に見る。ACO が隔離してジョブを畳んだあとでは判定できない。
            sequence.thenIdle(2);
            sequence.thenExecute(() -> {
                ExecutingCraftingJob job = null;
                for (var cpu : helper.getGrid(BlockPos.ZERO).getCraftingService().getCpus()) {
                    if (cpu instanceof CraftingCPUCluster cluster) {
                        var found = ((CraftingCpuLogicJobAccessor)
                                (Object) cluster.craftingLogic).insaneae$getJob();
                        if (found != null) {
                            job = found;
                            break;
                        }
                    }
                }
                helper.check(job != null, "投入したはずのジョブが CPU に無い");
                helper.check(
                        AcoExactJobOwnership.isAcoOwned(job),
                        "ACO 1.5.23 以降なのに exact ジョブの所有を認識できていない。"
                                + "ACO 側の判定メソッド名 (aco$isExactJob) が変わった可能性がある。"
                                + " job=" + job.getClass().getName()
                                + " 観測回数=" + jp.main.taikun.insaneae.integration.aco
                                        .AcoExactJobOwnership.observedAcoOwnedJobs);
            });
            sequence.thenSucceed();
        }).maxTicks(400);
    }

    /**
     * BigInteger クラフトストレージが、ACO の理論上限を容量として名乗れているか。
     *
     * <p>容量は {@code AcoBigIntegerLimitBridge} が ACO の公開 API
     * ({@code CAPACITY_LIMIT_API_VERSION} / {@code maximumSupportedAmount}) を反射で
     * 読んで決める。API が引けないと<b>例外を握り潰して long 互換容量へ退避</b>し、
     * long 超の注文が「容量不足」で弾かれる。実際 ACO の mc/1.21.1 にはこの API が
     * 移植されておらず、実機でそれが発生した。ACO 導入時だけ、退避していないことを見る。</p>
     */
    @TestPlot("insaneae_biginteger_cpu_capacity")
    public static void bigIntegerCpuCapacity(PlotBuilder plot) {
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("[0,2] 0 0");
        plot.blockState("2 [0,1] [0,2]", ModBlocks.BIG_INTEGER_CPU.get().defaultBlockState());

        plot.test(helper -> {
            var sequence = helper.startSequence();
            sequence.thenIdle(5);
            sequence.thenExecute(() -> {
                // ACO 無しなら long 互換容量が正しい姿なので、何も検査しない。
                if (AcoBigIntegerLimitBridge
                        .maximumSupportedAmount().isEmpty()) {
                    if (optionalClass(AcoClassNames.BIG_CRAFTING_ENGINE_API)
                            != null) {
                        throw new GameTestAssertException(
                                "ACO はあるのに理論上限を読めていない。"
                                        + "公開 API (CAPACITY_LIMIT_API_VERSION / "
                                        + "maximumSupportedAmount) がこの ACO に無い可能性が高い");
                    }
                    return;
                }

                BigInteger capacity = null;
                for (var cpu : helper.getGrid(BlockPos.ZERO).getCraftingService().getCpus()) {
                    if (cpu instanceof IBigCraftingCapacity exact) {
                        capacity = exact.insaneae$exactStorageCapacity();
                        break;
                    }
                }
                helper.check(capacity != null, "BigInteger CPU クラスタが見つからない");
                // long 上限ちょうどは「退避した long 互換容量」の値。超えていることを見る。
                helper.check(
                        capacity.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0,
                        "BigInteger クラフトストレージが long 互換容量へ退避している"
                                + " (容量=" + capacity + ")");
            });
            sequence.thenSucceed();
        }).maxTicks(60);
    }

    /**
     * <b>ACO 自身に BigInteger のまま実行させたとき、同じ木が完走すること。</b>
     *
     * <p>{@link #craftPastLongIntermediate} と盤面は同じで、違いは<b>納品先</b>だけ。
     * 監査対象の exact セル (ここでは完成品を設定した超強化クリエイティブセル) を
     * 置くと、ACO は所有権を手放さず {@code PhysicalCraftingTreeTransaction} で
     * 自分で実行する。中間素材は ACO の BigInteger エスクローに載るので、
     * <b>long のストレージを往復しない</b>。</p>
     *
     * <p>納品先が無いと ACO は所有を諦めて外部コンシューマ (こちらの Quantum CPU) へ
     * 委譲し、long の窓に刻む経路になる。どちらの経路も生きていることを見るために、
     * 2 本並べてある。</p>
     */
    @TestPlot("insaneae_aco_owned_intermediate")
    public static void acoOwnedIntermediate(PlotBuilder plot) {
        final long requested = 5_000_000_000_000_000_000L;
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("[0,3] 0 0");
        plot.blockEntity("1 0 0", AEBlocks.DRIVE, drive -> {
            // 原木の供給元であり、チェストの納品先でもある監査対象セル。
            drive.getInternalInventory().addItems(
                    ultraCreativeCell(Items.OAK_LOG, Items.CHEST));
        });
        plot.blockState("2 [0,1] [0,2]", ModBlocks.BIG_INTEGER_CPU.get().defaultBlockState());
        plot.block("2 1 0", AEBlocks.CRAFTING_ACCELERATOR);
        plot.blockState("3 0 0", ModBlocks.QUANTUM_CPU.get().defaultBlockState());

        plot.test(helper -> {
            // ACO の exact 実行が無ければ何も検査しない。
            if (optionalClass("com.syaru.ae2craftingoptimizer.access.ExactCraftingJobAccess") == null) {
                helper.startSequence().thenSucceed();
                return;
            }
            var state = new Object() {
                MachineSource source;
                Future<ICraftingPlan> plan;
                boolean submitted;
            };
            var sequence = helper.startSequence();

            sequence.thenExecute(() -> {
                var cpu = (QuantumCpuBlockEntity) helper.getBlockEntity(new BlockPos(3, 0, 0));
                cpu.getLogic().getPatternInv().addItems(
                        CraftingPatternHelper.encodeShapelessCraftingRecipe(helper.getLevel(),
                                new ItemStack(Items.OAK_LOG)));
                // 板 8 → チェスト 1。1.20.1 の AE2 テストヘルパーには定形レシピの
                // encode が無いので、公開 API へ直接レシピを渡して組む
                // (craftPastLongIntermediate と同じ理由)。
                ItemStack[] sparse = new ItemStack[9];
                for (int slot = 0; slot < sparse.length; slot++) {
                    sparse[slot] = slot == 4
                            ? ItemStack.EMPTY
                            : new ItemStack(Items.OAK_PLANKS);
                }
                var chestRecipe = helper.getLevel().getRecipeManager()
                        .byKey(ResourceLocation.fromNamespaceAndPath("minecraft", "chest"))
                        .orElseThrow(() -> new GameTestAssertException("チェストのレシピが無い"));
                cpu.getLogic().getPatternInv().addItems(
                        PatternDetailsHelper.encodeCraftingPattern(
                                (CraftingRecipe) chestRecipe,
                                sparse, new ItemStack(Items.CHEST), false, false));
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
                var result = helper.getGrid(BlockPos.ZERO).getCraftingService()
                        .submitJob(plan, null, null, false, state.source);
                state.submitted = result.successful();
                helper.check(state.submitted,
                        "納品先がある盤面なのに投入が断られた: errorCode=" + result.errorCode()
                                + " ACOの判断=" + acoPlanDiagnostics());
            });

            // <b>ACO が所有したことまで見る。</b>ここが委譲に変わると、テストは
            // 「速い long 窓経路」を検査しているだけになり、この 2 本を分けた意味が消える。
            sequence.thenIdle(2);
            sequence.thenExecute(() -> {
                ExecutingCraftingJob job = null;
                for (var cpu : helper.getGrid(BlockPos.ZERO).getCraftingService().getCpus()) {
                    if (cpu instanceof CraftingCPUCluster cluster) {
                        var found = ((CraftingCpuLogicJobAccessor)
                                (Object) cluster.craftingLogic).insaneae$getJob();
                        if (found != null) {
                            job = found;
                            break;
                        }
                    }
                }
                helper.check(job != null, "投入したはずのジョブが CPU に無い");
                helper.check(
                        AcoExactJobOwnership.isAcoOwned(job),
                        "納品先を用意したのに ACO が所有していない (long 窓へ委譲された)。"
                                + "ACOの判断=" + acoPlanDiagnostics());
            });

            // 実行が終わって CPU が空くこと。納品先がクリエイティブセルなので
            // 完成品の在庫では測れない。
            sequence.thenIdle(40);
            sequence.thenExecute(() -> {
                boolean busy = false;
                for (var cpu : helper.getGrid(BlockPos.ZERO).getCraftingService().getCpus()) {
                    busy |= cpu.isBusy();
                }
                helper.check(!busy,
                        "40 tick 経ってもジョブが終わらない。"
                                + "ACOの判断=" + acoPlanDiagnostics());
            });
            sequence.thenSucceed();
        }).maxTicks(600);
    }
}
