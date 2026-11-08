package jp.main.taikun.insaneae.testplots;

import static jp.main.taikun.insaneae.testplots.TestPlotSupport.acoPlanDiagnostics;
import static jp.main.taikun.insaneae.testplots.TestPlotSupport.optionalClass;
import static jp.main.taikun.insaneae.testplots.TestPlotSupport.ultraCreativeCell;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionHost;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.StorageCell;
import appeng.core.definitions.AEBlocks;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.helpers.MachineSource;
import appeng.server.testplots.CraftingPatternHelper;
import appeng.server.testplots.TestPlot;
import appeng.server.testworld.PlotBuilder;
import jp.main.taikun.insaneae.cell.InsaneUltraCreativeCellInventory;
import jp.main.taikun.insaneae.crafting.IBigCraftingCapacity;
import jp.main.taikun.insaneae.integration.aco.AcoBigIntegerLimitBridge;
import jp.main.taikun.insaneae.integration.aco.AcoClassNames;
import jp.main.taikun.insaneae.integration.aco.AcoExactJobOwnership;
import jp.main.taikun.insaneae.integration.aco.AcoExactLimits;
import jp.main.taikun.insaneae.integration.aco.InfiniteCellExactState;
import jp.main.taikun.insaneae.mixin.cpu.CraftingCpuLogicJobAccessor;
import jp.main.taikun.insaneae.quantum.QuantumCpuBlockEntity;
import jp.main.taikun.insaneae.registries.ModBlocks;
import jp.main.taikun.insaneae.registries.ModCells;
import jp.main.taikun.insaneae.registries.ModUpgrades;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.math.BigInteger;
import java.util.concurrent.Future;
//? if >=1.21 {
import appeng.server.testplots.TestPlotClass;
//?}
//? if <1.21 {
/*import appeng.api.crafting.PatternDetailsHelper;
import net.minecraft.world.item.crafting.CraftingRecipe;
*///?}

/**
 * Advanced Crafting Optimization (ACO) とのBigInteger連携。
 *
 * <p>走らせ方は {@link TestPlotSupport} を参照。</p>
 */
//? if >=1.21 {
@TestPlotClass
//?}
public final class AcoPlots {

    private AcoPlots() {
    }

    /**
     * ACOが居るとき、Quantum CPUが正確なBigInteger実行のターゲットとして見えることを確かめる。
     *
     * <p>ACOは「{@code ICraftingProvider} が {@code ProviderOwnedPatternBatchTarget} で、
     * 返したBlockEntityが {@code CraftingTableBatchTarget}」という形でターゲットを探す。
     * どちらか片方でも欠けると候補にすら入らず、エラーも出ずに別の経路へ回るので、
     * ここで両方が生えていることを見張る。</p>
     *
     * <p>ACOが無ければ何も検査せず成功する。Mixinごと適用されないのが正しい。
     * ACOの型を直接書かないのは、このテストが両方の環境で走るため。</p>
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

            // 超強化クリエイティブセルのBigInteger在庫。読みと書きで窓口が別なので両方見る
            //? if >=1.21 {
            var cell = new InsaneUltraCreativeCellInventory(
                    new ItemStack(ModCells.ULTRA_CREATIVE_CELL.get()));

            // 読み: ACO 1.5.20で入った公開契約。スナップショット側はこちらを先に見る
            Class<?> amountProvider = optionalClass("com.syaru.ae2craftingoptimizer.api.contract"
                    + ".ExactStorageAmountProvider");
            if (amountProvider == null) {
                // 1.5.20より古いACO。AcoMixinPluginがこの窓口だけ当てないので、
                // 生えていないのが正しい。内部のインターフェイス側だけで動くため
                helper.check(true, "");
            } else {
                helper.check(amountProvider.isInstance(cell),
                        "超強化クリエイティブセルに公開の ExactStorageAmountProvider が生えていない "
                                + "(AcoMixinPlugin の追加判定が効いていないかもしれない)");
            }
            //?}

            // 書き: 減らす側は1.5.22でもまだ内部accessしか知らない
            // 向こうの名前が変わるとエラーなしで効かなくなる — ここで気付けるようにする
            Class<?> exactStorage = optionalClass("com.syaru.ae2craftingoptimizer.access"
                    + ".ExtendedAePlusBigIntegerCellInventoryAccess");
            helper.check(exactStorage != null,
                    "ACO の ExtendedAePlusBigIntegerCellInventoryAccess が無い "
            //? if >=1.21 {
                            + "(書き込み側にも公開境界が入ったなら、こちらは畳んでよい)");
            //?} else {
                            /*+ "(公開フックへ移行できるかもしれない → ACO #101)");
            var cell = new InsaneUltraCreativeCellInventory(
                    new ItemStack(ModCells.ULTRA_CREATIVE_CELL.get()));
            *///?}
            helper.check(exactStorage != null && exactStorage.isInstance(cell),
                    "超強化クリエイティブセルに BigInteger 在庫の窓口が生えていない");

            // 在庫のマップは毎回まったく同じインスタンスであること
            // ACOはシミュレーションとコミットで == で突き合わせ、直接書き換えて在庫を減らす
            // コピーを返すと取引ごと巻き戻され、クラフトが進まないまま警告だけ出続ける
            helper.check(cell.insaneae$exactAmounts() == cell.insaneae$exactAmounts(),
                    "超強化クリエイティブセルが在庫マップのコピーを返している "
                            + "(ACO の同一性検査に落ちて取引が巻き戻される)");

            // 名乗る量がACOの計画エンジンの天井を越えていないこと
            // 越えるとBigCountMath.requireMaximumBitsが投げ、
            // このセルを入れただけであらゆるクラフトがWidePlanUnavailableになる
            // 上限はapi.contract.ExactCountLimits (1,048,576 bit) ではなく
            // ACOConfig.bigIntegerMaximumBitsは最大54,427 bitなので、取り違えないこと
            int ceiling = AcoExactLimits.gameplayMaximumBits();
            int advertised = InsaneUltraCreativeCellInventory
                    .exactAmount().bitLength();
            helper.check(advertised < ceiling,
                    "超強化クリエイティブセルが名乗る量 (" + advertised + " bit) が "
                            + "ACO の上限 (" + ceiling + " bit) を越えている");
            // 種類数を掛けた合計や複数セルの合算にも余地が要る
            helper.check(advertised <= ceiling / 2,
                    "名乗る量 (" + advertised + " bit) に足し算の余地が無い "
                            + "(ACO の上限 " + ceiling + " bit の半分までにすること)");
        }).thenSucceed());
    }

    /**
     * ACO 1.5.23以降、exactジョブの実行をACOが所有していることをこちらが認識できるか。
     *
     * <p>{@code executeCrafting} の @HEADにはこちらのまとめ処理とACOの打ち切りが両方注入されて
     * いて、実機の適用順ではこちらが先に走る。ACOの所有を見落とすと、ACOの台帳に無い実行を
     * 1回進めてしまう。ここで検査するのは判定そのもの — ACOが生やすメソッド名
     * ({@code aco$isExactJob}) は相手の都合で変わりうるのに、変わってもエラーにならず
     * falseが返るだけになるため、注入の有無ではなく実ジョブに対する戻り値で見張る。</p>
     *
     * <p>ACOが1.5.22以下の環境では、共通契約のクラスが無いので何も検査しない。</p>
     */
    @TestPlot("insaneae_aco_exact_ownership")
    public static void acoExactOwnership(PlotBuilder plot) {
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("[0,3] 0 0");
        plot.blockEntity("1 0 0", AEBlocks.DRIVE, drive -> {
            // 完成品のチェストもセルに設定してある。ACOのexact実行は境界の入出力を
            // exact書き込み可能セルでしか行えず、納品先が無い計画は所有権を取らずに
            // 外部コンシューマへ委譲する。このテストの主題は所有の認識なので、
            // 所有が成立する盤面にしておく。チェストにするのは、余りの板材が出ないようにするため
            // ACO 2.0は余りの納品先も要求する。ボタンは原木1本から板材4枚のうち1枚を取るので、
            // 板材をセルに設定しただけでは所有されなかった
            drive.getInternalInventory().addItems(ultraCreativeCell(Items.OAK_LOG, Items.CHEST));
        });
        plot.blockState("2 [0,1] [0,2]", ModBlocks.BIG_INTEGER_CPU.get().defaultBlockState());
        plot.block("2 1 0", AEBlocks.CRAFTING_ACCELERATOR);
        plot.blockState("3 0 0", ModBlocks.QUANTUM_CPU.get().defaultBlockState());

        plot.test(helper -> {
            // 共通契約は、ACO 1.5.23でAAE専用から切り出された。無ければ何も検査しない
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
                state.source = new MachineSource(
                        (IActionHost)
                                helper.getBlockEntity(new BlockPos(3, 0, 0)));
                state.plan = helper.getGrid(BlockPos.ZERO).getCraftingService()
                        .beginCraftingCalculation(helper.getLevel(), () -> state.source,
                                AEItemKey.of(Items.CHEST), 5_000_000_000_000_000_000L,
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

                // 投入と同じtickのうちに見る。ACO 2.0は所有したジョブをそのtickのうちに
                // 終わらせるので、1 tickでも待つとジョブがCPUに残っていない
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
     * BigIntegerクラフトストレージが、ACOの理論上限を容量として名乗れているか。
     *
     * <p>容量は {@code AcoBigIntegerLimitBridge} がACOの公開APIの
     * {@code CAPACITY_LIMIT_API_VERSION} と {@code maximumSupportedAmount} を反射で
     * 読んで決める。APIが引けないと例外を握り潰してlong互換容量へ退避し、
     * long超の注文が「容量不足」で弾かれる。ACOのmc/1.21.1にはこのAPIが移植されていないので、
     * ACO導入時だけ、退避していないことを見る。</p>
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
                // ACO無しならlong互換容量が正しい姿なので、何も検査しない
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

                //? if >=1.21 {
                java.math.BigInteger capacity = null;
                //?} else {
                /*BigInteger capacity = null;
                *///?}
                for (var cpu : helper.getGrid(BlockPos.ZERO).getCraftingService().getCpus()) {
                    if (cpu instanceof IBigCraftingCapacity exact) {
                        capacity = exact.insaneae$exactStorageCapacity();
                        break;
                    }
                }
                helper.check(capacity != null, "BigInteger CPU クラスタが見つからない");
                // long上限ちょうどは「退避したlong互換容量」の値。超えていることを見る
                helper.check(
                        //? if >=1.21 {
                        capacity.compareTo(java.math.BigInteger.valueOf(Long.MAX_VALUE)) > 0,
                        //?} else {
                        /*capacity.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0,
                        *///?}
                        "BigInteger クラフトストレージが long 互換容量へ退避している"
                                + " (容量=" + capacity + ")");
            });
            sequence.thenSucceed();
        }).maxTicks(60);
    }

    /**
     * ACO自身にBigIntegerのまま実行させたとき、同じ木が完走すること。
     *
     * <p>{@link #craftPastLongIntermediate} と盤面は同じで、違いは納品先だけ。
     * 監査対象のexactセルには、完成品を設定した超強化クリエイティブセルを置く。
     * そうするとACOは所有権を手放さず {@code PhysicalCraftingTreeTransaction} で
     * 自分で実行する。中間素材はACOのBigIntegerエスクローに載るので、
     * longのストレージを往復しない。</p>
     *
     * <p>納品先が無いとACOは所有を諦め、外部コンシューマであるこちらのQuantum CPUへ
     * 委譲し、longの窓に刻む経路になる。どちらの経路も生きていることを見るために、
     * 2本並べてある。</p>
     */
    @TestPlot("insaneae_aco_owned_intermediate")
    public static void acoOwnedIntermediate(PlotBuilder plot) {
        final long requested = 5_000_000_000_000_000_000L;
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("[0,3] 0 0");
        plot.blockEntity("1 0 0", AEBlocks.DRIVE, drive -> {
            // 原木の供給元であり、チェストの納品先でもある監査対象セル
            drive.getInternalInventory().addItems(
                    ultraCreativeCell(Items.OAK_LOG, Items.CHEST));
        });
        plot.blockState("2 [0,1] [0,2]", ModBlocks.BIG_INTEGER_CPU.get().defaultBlockState());
        plot.block("2 1 0", AEBlocks.CRAFTING_ACCELERATOR);
        plot.blockState("3 0 0", ModBlocks.QUANTUM_CPU.get().defaultBlockState());

        plot.test(helper -> {
            // ACOのexact実行が無ければ何も検査しない
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
                var result = helper.getGrid(BlockPos.ZERO).getCraftingService()
                        .submitJob(plan, null, null, false, state.source);
                state.submitted = result.successful();
                helper.check(state.submitted,
                        "納品先がある盤面なのに投入が断られた: errorCode=" + result.errorCode()
                                + " ACOの判断=" + acoPlanDiagnostics());
            });

            // ACOが所有したことまで見る。ここが委譲に変わると、テストは
            // 「速いlong窓経路」を検査しているだけになり、この2本を分けた意味が消える
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

            // 実行が終わってCPUが空くこと。納品先がクリエイティブセルなので
            // 完成品の在庫では測れない
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

    private static String describe(KeyCounter counter) {
        var text = new StringBuilder("{");
        for (var entry : counter) {
            text.append(entry.getKey()).append('=').append(entry.getLongValue()).append(' ');
        }
        return text.append('}').toString();
    }

    /** ME Infinity Cellの無限丸石セル。Modが居なければ空。 */
    private static ItemStack infinityCobblestoneCell() {
        var item = BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("meinfinitycell", "infinity_cobblestone_cell"));
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    /**
     * ME Infinity Cellの無限セルが、ACOにBigIntegerの在庫を名乗ること
     * CurseForgeで報告された、21億を超えるとME Infinity CellがBigIntegerで扱われない件の回帰テスト。
     *
     * <p>そのセルはAE2に {@code Integer.MAX_VALUE} 個しか申告しない。かまどは丸石8個でできるので、
     * かまどを5×10^18個頼むと丸石は4×10^19個要り、longも超えるのでACOのBigInteger計画になる。
     * セルがBigIntegerの在庫を名乗っていなければ計画は材料不足で終わる。</p>
     *
     * <p>ACOかME Infinity Cellのどちらかが居なければ何も検査しない。
     * 走らせるには{@code -PwithAco=true -PwithInfinityCell=true}を付ける。</p>
     */
    @TestPlot("insaneae_infinity_cell_exact_supply")
    public static void infinityCellExactSupply(PlotBuilder plot) {
        final long requested = 5_000_000_000_000_000_000L;
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("[0,3] 0 0");
        plot.blockEntity("1 0 0", AEBlocks.DRIVE, drive -> {
            ItemStack infinity = infinityCobblestoneCell();
            if (!infinity.isEmpty()) {
                drive.getInternalInventory().addItems(infinity);
            }
            // 完成品の納品先。ACOが実行を所有するのに要るが、丸石はここに入れない
            drive.getInternalInventory().addItems(ultraCreativeCell(Items.FURNACE));
        });
        plot.blockState("2 [0,1] [0,2]", ModBlocks.BIG_INTEGER_CPU.get().defaultBlockState());
        plot.block("2 1 0", AEBlocks.CRAFTING_ACCELERATOR);
        plot.blockState("3 0 0", ModBlocks.QUANTUM_CPU.get().defaultBlockState());

        plot.test(helper -> {
            ItemStack infinity = infinityCobblestoneCell();
            if (infinity.isEmpty()
                    || optionalClass("com.syaru.ae2craftingoptimizer.access.ExactCraftingJobAccess") == null) {
                helper.startSequence().thenSucceed();
                return;
            }
            var state = new Object() {
                MachineSource source;
                Future<ICraftingPlan> plan;
            };
            var sequence = helper.startSequence();

            sequence.thenExecute(() -> {
                // セル単体の窓口。ACOの版で有る無しが違うので、在る方だけ見る
                StorageCell cell = StorageCells.getCellInventory(infinity, null);
                helper.check(cell != null, "無限丸石セルの中身が取れない");
                Class<?> exactStorage = optionalClass("com.syaru.ae2craftingoptimizer.access"
                        + ".ExtendedAePlusBigIntegerCellInventoryAccess");
                helper.check(exactStorage != null && exactStorage.isInstance(cell),
                        "無限セルに BigInteger 在庫の窓口が生えていない (@Pseudo Mixin が当たっていない)");
                Class<?> amountProvider = optionalClass("com.syaru.ae2craftingoptimizer.api.contract"
                        + ".ExactStorageAmountProvider");
                if (amountProvider != null) {
                    helper.check(amountProvider.isInstance(cell),
                            "無限セルに公開の ExactStorageAmountProvider が生えていない");
                }
                var holder = (InfiniteCellExactState.Holder) cell;
                var amounts = holder.insaneae$infiniteExactState().amounts();
                helper.check(amounts == holder.insaneae$infiniteExactState().amounts(),
                        "無限セルが在庫マップのコピーを返している (ACO の同一性検査に落ちる)");
                // ACO 2.0はAE2側の在庫がlongの天井に張り付いていないとBigInteger計画に入らない
                var facade = new KeyCounter();
                cell.getAvailableStacks(facade);
                helper.check(facade.get(AEItemKey.of(Items.COBBLESTONE)) == Long.MAX_VALUE,
                        "無限セルが AE2 に申告する丸石が long の天井まで上がっていない: "
                                + facade.get(AEItemKey.of(Items.COBBLESTONE)));
                BigInteger cobblestone = amounts.get(AEItemKey.of(Items.COBBLESTONE));
                helper.check(cobblestone != null
                                && cobblestone.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0,
                        "無限セルが名乗る丸石が long を超えていない: " + cobblestone);

                var cpu = (QuantumCpuBlockEntity) helper.getBlockEntity(new BlockPos(3, 0, 0));
                //? if >=1.21 {
                Object[] furnace = new Object[] {
                        Items.COBBLESTONE, Items.COBBLESTONE, Items.COBBLESTONE,
                        Items.COBBLESTONE, null, Items.COBBLESTONE,
                        Items.COBBLESTONE, Items.COBBLESTONE, Items.COBBLESTONE};
                //?} else {
                /*ItemStack[] sparse = new ItemStack[9];
                for (int slot = 0; slot < sparse.length; slot++) {
                    sparse[slot] = slot == 4 ?
                            ItemStack.EMPTY
                            : new ItemStack(Items.COBBLESTONE);
                }
                var furnaceRecipe = helper.getLevel().getRecipeManager()
                        .byKey(ResourceLocation.fromNamespaceAndPath("minecraft", "furnace"))
                        .orElseThrow(() -> new GameTestAssertException("かまどのレシピが無い"));
                *///?}
                cpu.getLogic().getPatternInv().addItems(
                        //? if >=1.21 {
                        CraftingPatternHelper.encodeCraftingPattern(
                                helper.getLevel(), furnace, false, false));
                        //?} else {
                        /*PatternDetailsHelper.encodeCraftingPattern(
                                (CraftingRecipe) furnaceRecipe,
                                sparse, new ItemStack(Items.FURNACE), false, false));
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
                        (IActionHost) helper.getBlockEntity(new BlockPos(3, 0, 0)));
                state.plan = grid.getCraftingService().beginCraftingCalculation(
                        helper.getLevel(), () -> state.source,
                        AEItemKey.of(Items.FURNACE), requested,
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
                        "丸石が足りない計画になった (無限セルの BigInteger 在庫が計画に入っていない): 不足 "
                                + describe(plan.missingItems()) + " 消費 " + describe(plan.usedItems())
                                + " パターン " + plan.patternTimes().size()
                                + " ACOの判断=" + acoPlanDiagnostics());
                var result = helper.getGrid(BlockPos.ZERO).getCraftingService()
                        .submitJob(plan, null, null, false, state.source);
                helper.check(result.successful(),
                        "投入が断られた: errorCode=" + result.errorCode()
                                + " ACOの判断=" + acoPlanDiagnostics());
            });

            sequence.thenIdle(40);
            sequence.thenExecute(() -> {
                boolean busy = false;
                for (var cpu : helper.getGrid(BlockPos.ZERO).getCraftingService().getCpus()) {
                    busy |= cpu.isBusy();
                }
                helper.check(!busy,
                        "40 tick 経ってもジョブが終わらない。ACOの判断=" + acoPlanDiagnostics());
            });
            sequence.thenSucceed();
        }).maxTicks(600);
    }
}
