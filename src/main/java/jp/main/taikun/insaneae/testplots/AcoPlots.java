package jp.main.taikun.insaneae.testplots;

import static jp.main.taikun.insaneae.testplots.TestPlotSupport.acoPlanDiagnostics;
import static jp.main.taikun.insaneae.testplots.TestPlotSupport.optionalClass;
import static jp.main.taikun.insaneae.testplots.TestPlotSupport.ultraCreativeCell;

import appeng.api.crafting.IPatternDetails;
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
import appeng.server.testplots.TestPlotClass;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.math.BigInteger;
import java.util.concurrent.Future;

/**
 * Advanced Crafting Optimization (ACO) との BigInteger 連携。
 *
 * <p>走らせ方は {@link TestPlotSupport} を参照。</p>
 */
@TestPlotClass
public final class AcoPlots {

    private AcoPlots() {
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
            var cell = new InsaneUltraCreativeCellInventory(
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
     * <p>さらに {@link #acoGraphProbe} で ACO の compiled graph を直接覗くと、
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
     * 読んで決める。API が引けないと<b>例外を握り潰して long 互換容量へ静かに退避</b>し、
     * long 超の注文が「容量不足」で弾かれる。実際 ACO の mc/1.21.1 にはこの API が
     * 移植されておらず、実機でそれを踏んだ。ACO 導入時だけ、退避していないことを見る。</p>
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

                java.math.BigInteger capacity = null;
                for (var cpu : helper.getGrid(BlockPos.ZERO).getCraftingService().getCpus()) {
                    if (cpu instanceof IBigCraftingCapacity exact) {
                        capacity = exact.insaneae$exactStorageCapacity();
                        break;
                    }
                }
                helper.check(capacity != null, "BigInteger CPU クラスタが見つからない");
                // long 上限ちょうどは「退避した long 互換容量」の値。超えていることを見る。
                helper.check(
                        capacity.compareTo(java.math.BigInteger.valueOf(Long.MAX_VALUE)) > 0,
                        "BigInteger クラフトストレージが long 互換容量へ退避している"
                                + " (容量=" + capacity + ")");
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
