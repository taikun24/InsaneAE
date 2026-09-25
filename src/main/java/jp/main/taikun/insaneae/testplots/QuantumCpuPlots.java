package jp.main.taikun.insaneae.testplots;

import static jp.main.taikun.insaneae.testplots.TestPlotSupport.storedAmount;
import static jp.main.taikun.insaneae.testplots.TestPlotSupport.ultraCreativeCell;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.crafting.execution.ElapsedTimeTracker;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.items.storage.CreativeCellItem;
import appeng.me.service.CraftingService;
import appeng.server.testplots.CraftingPatternHelper;
import appeng.server.testplots.TestPlot;
import appeng.server.testplots.TestPlotClass;
import appeng.server.testworld.PlotBuilder;
import appeng.server.testworld.PlotTestHelper;
import appeng.server.testworld.TestCraftingJob;
import jp.main.taikun.insaneae.integration.aco.AcoBigIntegerJobRegistry;
import jp.main.taikun.insaneae.quantum.CraftingJobView;
import jp.main.taikun.insaneae.quantum.QuantumBulkCrafting;
import jp.main.taikun.insaneae.quantum.QuantumCpuBlockEntity;
import jp.main.taikun.insaneae.quantum.ReflectiveCraftingJobView;
import jp.main.taikun.insaneae.quantum.TimeTrackerAdapter;
import jp.main.taikun.insaneae.registries.ModBlocks;
import jp.main.taikun.insaneae.registries.ModUpgrades;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Quantum CPU (まとめクラフト・内蔵 CPU・タスク統合カード・完成待ち帳簿) の検証。
 *
 * <p>走らせ方は {@link TestPlotSupport} を参照。</p>
 */
@TestPlotClass
public final class QuantumCpuPlots {

    private QuantumCpuPlots() {
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
                TimeTrackerAdapter.addMaxItems(
                        tracker, 7, AEKeyType.items());
                helper.check(tracker.max == 7,
                        "自前型カウンタへの加算が効いていない: " + tracker.max);

                // AAE と同じフィールド構造のフェイク CPU がレイアウト検査を通ること
                var logic = new FakeForeignCpuLogic();
                logic.job.tasks.put(patterns.get(0), new ForeignTaskProgress(requested));
                logic.inventory.insert(AEItemKey.of(Items.OAK_LOG), logsInStock,
                        Actionable.MODULATE);

                var view = ReflectiveCraftingJobView.of(logic);
                helper.check(view != null,
                        "自前カウンタ型を持つ CPU がレイアウト検査で弾かれた (Issue #2 の再発)");

                var grid = helper.getGrid(BlockPos.ZERO);
                int pushed = QuantumBulkCrafting.execute(
                        view, (int) requested,
                        (CraftingService) grid.getCraftingService(),
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
        final Map<IPatternDetails, ForeignTaskProgress> tasks = new HashMap<>();
        final ListCraftingInventory waitingFor =
                new ListCraftingInventory(what -> {
                });
        final ForeignTimeTracker timeTracker = new ForeignTimeTracker();
    }

    /** AAE の AdvCraftingCPULogic に相当するフィールド構造。 */
    private static final class FakeForeignCpuLogic {
        final ForeignExecutingJob job = new ForeignExecutingJob();
        final ListCraftingInventory inventory =
                new ListCraftingInventory(what -> {
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
                var tag = new CompoundTag();
                cpu.saveAdditional(tag, registries);
                cpu.loadTag(tag, registries);
                helper.check(overLong.equals(cpu.getPendingOutputs().get(log)),
                        "NBT 往復で量がずれた: " + cpu.getPendingOutputs().get(log));

                // 3) 壊れたエントリは例外なしで捨てられ、正常なエントリは残る
                var broken = tag.copy();
                var entries = broken.getCompound("pendingOutputsBig")
                        .getList("entries", Tag.TAG_COMPOUND);
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
                var legacy = new CompoundTag();
                cpu.saveAdditional(legacy, registries);
                legacy.remove("pendingOutputsBig");
                var legacyList = new ListTag();
                legacyList.add(GenericStack.writeTag(registries,
                        new GenericStack(log, 123_456_789L)));
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
                        Actionable.MODULATE);

                state.pushed = QuantumBulkCrafting.execute(
                        state.view, (int) requested,
                        (CraftingService) grid.getCraftingService(),
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
                        Actionable.MODULATE);

                state.ops = QuantumBulkCrafting.execute(
                        state.view, clusterBudget,
                        (CraftingService) grid.getCraftingService(),
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
                        Actionable.MODULATE);

                // Exact 台帳は ExecutingCraftingJob をキーにするが、ここでは本物のジョブを
                // 立てずに経路だけ試したいので null をキーに使う (WeakHashMap は null を許す)。
                // このプロットしか null を使わないが、並列で走る他プロットに残さないよう最後に消す。
                AcoBigIntegerJobRegistry.install(null,
                        Map.of(patterns.get(0), java.math.BigInteger.valueOf(requested)));
                state.view.exactCursor = AcoBigIntegerJobRegistry.find(null)
                        .orElseThrow(() -> new GameTestAssertException("Exact 台帳を作れなかった"))
                        .cursor(details -> {
                        });

                state.ops = QuantumBulkCrafting.execute(
                        state.view, clusterBudget,
                        (CraftingService) grid.getCraftingService(),
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

    /** 中身を設定した超強化クリエイティブセルを 1 枚作る。 */
    /**
     * <b>Quantum CPU の内部スロットに挿したクラフトユニットが、本物のクラフト CPU になるか。</b>
     *
     * <p>ネットワークには<b>他にクラフト CPU を 1 つも置いていない</b>。
     * そのため「CPU 一覧に出る」「発注が通る」「最後まで終わる」がどれも
     * <b>内蔵 CPU のおかげであること</b>が確定する。</p>
     *
     * <p>見ているのは 4 点。</p>
     * <ol>
     *   <li>ユニットを挿す<b>前</b>は CPU が 0 個。クラフトストレージが無ければ
     *       CPU として名乗らない ({@code QuantumCraftingCpu#isFormed})。</li>
     *   <li>挿すと CPU が 1 個現れ、容量とスレッド数が挿したユニットの合計になる。
     *       ここが 0 だと {@code CraftingCPUClusterMixin} の数え直しが
     *       内蔵 CPU 用の経路に入っていない。</li>
     *   <li>2 段クラフト (ボタン &lt;- 板材 &lt;- 原木) が最後まで終わる。</li>
     *   <li>ユニットを抜くと CPU 一覧から消える。</li>
     * </ol>
     */
    @TestPlot("insaneae_quantum_cpu_internal_units")
    public static void quantumCpuInternalUnits(PlotBuilder plot) {
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("[0,3] 0 0");
        plot.blockEntity("1 0 0", AEBlocks.DRIVE, drive -> {
            drive.getInternalInventory().addItems(ultraCreativeCell(Items.OAK_LOG));
            drive.getInternalInventory().addItems(AEItems.ITEM_CELL_64K.stack());
        });
        plot.blockState("3 0 0", ModBlocks.QUANTUM_CPU.get().defaultBlockState());

        final long requested = 64;
        final int storageUnits = 2;
        final int acceleratorUnits = 3;

        plot.test(helper -> {
            var cpuPos = new BlockPos(3, 0, 0);
            var state = new Object() {
                TestCraftingJob job;
            };
            var sequence = helper.startSequence();

            sequence.thenExecute(() -> {
                var cpu = (QuantumCpuBlockEntity) helper.getBlockEntity(cpuPos);
                // 2 段のツリー: ボタン <- 板材 <- 原木 (在庫にあるのは原木だけ)。
                cpu.getLogic().getPatternInv().addItems(
                        CraftingPatternHelper.encodeShapelessCraftingRecipe(helper.getLevel(),
                                new ItemStack(Items.OAK_LOG)));
                cpu.getLogic().getPatternInv().addItems(
                        CraftingPatternHelper.encodeShapelessCraftingRecipe(helper.getLevel(),
                                new ItemStack(Items.OAK_PLANKS)));
            });
            sequence.thenIdle(10);

            // 1. ユニットを挿す前は CPU が無いこと。
            sequence.thenExecute(() -> helper.check(countCpus(helper) == 0,
                    "クラフトユニットを挿していないのに CPU が現れている: " + countCpus(helper)
                            + " (isFormed の判定か、他に CPU が置かれている)", cpuPos));

            // 2. クラフトストレージと協調処理ユニットを内部スロットへ。
            sequence.thenExecute(() -> {
                var cpu = (QuantumCpuBlockEntity) helper.getBlockEntity(cpuPos);
                cpu.getCraftingUnits().addItems(
                        AEBlocks.CRAFTING_STORAGE_64K.stack(storageUnits));
                cpu.getAcceleratorUnits().addItems(
                        AEBlocks.CRAFTING_ACCELERATOR.stack(acceleratorUnits));
            });
            sequence.thenIdle(10);

            sequence.thenExecute(() -> {
                helper.check(countCpus(helper) == 1,
                        "内蔵クラフト CPU が CPU 一覧に出ていない: " + countCpus(helper)
                                + " (CraftingServiceQuantumCpuMixin が当たっていない可能性)", cpuPos);

                var cpu = (QuantumCpuBlockEntity) helper.getBlockEntity(cpuPos);
                var cluster = cpu.getCraftingCpu().cluster();
                long expectedBytes =
                        AEBlocks.CRAFTING_STORAGE_64K.block().type.getStorageBytes() * storageUnits;
                helper.check(cluster.getAvailableStorage() == expectedBytes,
                        "内蔵 CPU の容量が挿したユニットの合計になっていない: "
                                + cluster.getAvailableStorage() + " != " + expectedBytes, cpuPos);
                int expectedThreads =
                        AEBlocks.CRAFTING_ACCELERATOR.block().type.getAcceleratorThreads()
                                * acceleratorUnits;
                helper.check(cluster.getCoProcessors() == expectedThreads,
                        "内蔵 CPU のスレッド数が合っていない: " + cluster.getCoProcessors()
                                + " != " + expectedThreads, cpuPos);
            });

            // 3. 実際に 2 段クラフトが最後まで終わること。
            sequence.thenExecute(() -> state.job = new TestCraftingJob(
                    helper, BlockPos.ZERO, AEItemKey.of(Items.OAK_BUTTON), requested));
            sequence.thenWaitUntil(() -> state.job.tickUntilStarted());
            sequence.thenWaitUntil(() -> {
                long stored = storedAmount(helper, Items.OAK_BUTTON);
                if (stored < requested) {
                    throw new GameTestAssertException("内蔵 CPU での 2 段クラフトが "
                            + stored + "/" + requested + " しか進まない");
                }
            });

            // 4. 抜いたら CPU 一覧から消えること。
            sequence.thenExecute(() -> {
                var cpu = (QuantumCpuBlockEntity) helper.getBlockEntity(cpuPos);
                cpu.getCraftingUnits().clear();
                cpu.getAcceleratorUnits().clear();
                // clear() は 1 枠ずつの通知を出さないので、数え直しを促す。
                cpu.getCraftingCpu().updateUnits();
            });
            sequence.thenIdle(10);
            sequence.thenExecute(() -> helper.check(countCpus(helper) == 0,
                    "ユニットを抜いても CPU 一覧に残っている: " + countCpus(helper), cpuPos));

            sequence.thenSucceed();
        // 既定の持ち時間ではネットワークの起動 + 2 段クラフトが終わらない
        // (他のクラフト完走テストと同じ 400 tick)。
        }).maxTicks(400);
    }

    /**
     * <b>内蔵 CPU が容量を切り分けて、2 本のクラフトを同時に受けるか。</b>
     *
     * <p>AE2 のクラフト CPU は 1 台 1 ジョブで、実行中は容量が丸ごと塞がる。
     * Quantum CPU は容量を発注のたびに切り出すので、
     * <b>1 本走らせたまま残量ぶんの発注ができる</b> ({@code QuantumCraftingCpu})。</p>
     *
     * <p>見ているのは 4 点。</p>
     * <ol>
     *   <li>1 本目を始めると、CPU 一覧が<b>実行中のぶんと残量ぶんの 2 行</b>になる。</li>
     *   <li>残量が「合計 - 1 本目が要求したバイト数」に減っている
     *       (塞がるのは使うぶんだけ = 切り分けが効いている)。</li>
     *   <li>1 本目を止めたまま<b>2 本目を発注できる</b>。
     *       ここが通らないと「実行中は発注できない」元の挙動のまま。</li>
     *   <li>両方終わると区画が畳まれ、CPU 一覧も容量も元に戻る。</li>
     * </ol>
     *
     * <p>1 本目は<b>わざと一時停止</b>して止めてある ({@code setJobSuspended})。
     * そうしないと 2 本目を出す前に終わってしまい、同時に走っているかを見られない。</p>
     */
    @TestPlot("insaneae_quantum_cpu_split")
    public static void quantumCpuSplit(PlotBuilder plot) {
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("[0,3] 0 0");
        plot.blockEntity("1 0 0", AEBlocks.DRIVE, drive -> {
            drive.getInternalInventory().addItems(ultraCreativeCell(Items.OAK_LOG));
            drive.getInternalInventory().addItems(AEItems.ITEM_CELL_64K.stack());
        });
        plot.blockState("3 0 0", ModBlocks.QUANTUM_CPU.get().defaultBlockState());

        final long buttons = 64;
        final long planks = 32;
        final int storageUnits = 4;

        plot.test(helper -> {
            var cpuPos = new BlockPos(3, 0, 0);
            var state = new Object() {
                TestCraftingJob first;
                TestCraftingJob second;
                BigInteger total = BigInteger.ZERO;
                BigInteger freeAfterFirst = BigInteger.ZERO;
                ItemStack heldPattern = ItemStack.EMPTY;
            };
            var sequence = helper.startSequence();

            sequence.thenExecute(() -> {
                var cpu = (QuantumCpuBlockEntity) helper.getBlockEntity(cpuPos);
                // 2 段のツリー: ボタン <- 板材 <- 原木 (在庫にあるのは原木だけ)。
                cpu.getLogic().getPatternInv().addItems(
                        CraftingPatternHelper.encodeShapelessCraftingRecipe(helper.getLevel(),
                                new ItemStack(Items.OAK_LOG)));
                cpu.getLogic().getPatternInv().addItems(
                        CraftingPatternHelper.encodeShapelessCraftingRecipe(helper.getLevel(),
                                new ItemStack(Items.OAK_PLANKS)));
                // 協調処理ユニットは挿さない。スレッドが 0 でも区画は切れること。
                cpu.getCraftingUnits().addItems(AEBlocks.CRAFTING_STORAGE_64K.stack(storageUnits));
            });
            sequence.thenIdle(10);

            // 1. 発注前は CPU 1 台、容量は挿したぶん全部。
            sequence.thenExecute(() -> {
                var cpu = (QuantumCpuBlockEntity) helper.getBlockEntity(cpuPos);
                state.total = cpu.getCraftingCpu().totalStorage();
                helper.check(countCpus(helper) == 1,
                        "発注前なのに CPU が " + countCpus(helper) + " 台ある", cpuPos);
                helper.check(cpu.getCraftingCpu().freeStorage().equals(state.total),
                        "発注前なのに空き容量が合計と違う: "
                                + cpu.getCraftingCpu().freeStorage() + " != " + state.total, cpuPos);
            });

            // 2. 1 本目を始めて、すぐ止める (終わってしまう前に)。
            sequence.thenExecute(() -> state.first = new TestCraftingJob(
                    helper, BlockPos.ZERO, AEItemKey.of(Items.OAK_BUTTON), buttons));
            // 止めるのは<b>始まったのと同じ tick のうち</b>に。1 tick でも空けると
            // 先に終わってしまい、同時に走っているところを見られない。
            sequence.thenWaitUntil(() -> {
                state.first.tickUntilStarted();
                state.heldPattern = takeButtonPattern(helper, cpuPos);
            });
            // CPU 名簿の作り直しは tick の終わりなので、数えるのは少し待ってから。
            sequence.thenIdle(5);

            sequence.thenExecute(() -> {
                var cpu = (QuantumCpuBlockEntity) helper.getBlockEntity(cpuPos);
                helper.check(cpu.getCraftingCpu().runningJobs() == 1,
                        "実行中の区画が 1 つになっていない: "
                                + cpu.getCraftingCpu().runningJobs(), cpuPos);
                // 実行中のぶんと残量ぶんで 2 行。
                helper.check(countCpus(helper) == 2,
                        "実行中でも残量ぶんの CPU が出ていない: " + countCpus(helper)
                                + " 台 (区画の切り出しが効いていない)", cpuPos);
                state.freeAfterFirst = cpu.getCraftingCpu().freeStorage();
                helper.check(state.freeAfterFirst.signum() > 0,
                        "1 本走らせただけで空き容量が 0 になっている"
                                + " (容量が丸ごと塞がっている)", cpuPos);
                helper.check(state.freeAfterFirst.compareTo(state.total) < 0,
                        "1 本走らせても空き容量が減っていない: "
                                + state.freeAfterFirst + " == " + state.total, cpuPos);
            });

            // 3. 止めたまま 2 本目を発注できること。
            sequence.thenExecute(() -> state.second = new TestCraftingJob(
                    helper, BlockPos.ZERO, AEItemKey.of(Items.OAK_PLANKS), planks));
            sequence.thenWaitUntil(() -> {
                state.second.tickUntilStarted();
                var cpu = (QuantumCpuBlockEntity) helper.getBlockEntity(cpuPos);
                int running = cpu.getCraftingCpu().runningJobs();
                if (running != 2) {
                    throw new GameTestAssertException(
                            "2 本目が別の区画で走っていない: 実行中 " + running + " 本");
                }
            });
            sequence.thenIdle(5);
            sequence.thenExecute(() -> helper.check(countCpus(helper) == 3,
                    "2 本実行中 + 残量ぶんで 3 台にならない: " + countCpus(helper) + " 台",
                    cpuPos));

            // 4. パターンを戻したら両方終わり、区画も容量も元に戻ること。
            sequence.thenExecute(() -> {
                var cpu = (QuantumCpuBlockEntity) helper.getBlockEntity(cpuPos);
                cpu.getLogic().getPatternInv().setItemDirect(BUTTON_PATTERN_SLOT,
                        state.heldPattern);
            });
            sequence.thenWaitUntil(() -> {
                long madeButtons = storedAmount(helper, Items.OAK_BUTTON);
                if (madeButtons < buttons) {
                    throw new GameTestAssertException("1 本目 (ボタン) が "
                            + madeButtons + "/" + buttons + " しか進まない");
                }
                long madePlanks = storedAmount(helper, Items.OAK_PLANKS);
                if (madePlanks < planks) {
                    throw new GameTestAssertException("2 本目 (板材) が "
                            + madePlanks + "/" + planks + " しか進まない");
                }
            });
            sequence.thenWaitUntil(() -> {
                var cpu = (QuantumCpuBlockEntity) helper.getBlockEntity(cpuPos);
                if (cpu.getCraftingCpu().runningJobs() != 0) {
                    throw new GameTestAssertException("終わった区画が畳まれていない: 実行中 "
                            + cpu.getCraftingCpu().runningJobs() + " 本");
                }
                if (!cpu.getCraftingCpu().freeStorage().equals(state.total)) {
                    throw new GameTestAssertException("空き容量が元に戻っていない: "
                            + cpu.getCraftingCpu().freeStorage() + " != " + state.total);
                }
                if (countCpus(helper) != 1) {
                    throw new GameTestAssertException(
                            "CPU 一覧が 1 台に戻っていない: " + countCpus(helper) + " 台");
                }
            });

            sequence.thenSucceed();
        // 2 本ぶんのクラフトと、その間の待ちを入れても収まる長さ。
        }).maxTicks(600);
    }

    /** ボタンのパターンを入れてある枠 (板材のパターンの次に入れているので 1 番)。 */
    private static final int BUTTON_PATTERN_SLOT = 1;

    /**
     * ボタンのパターンを引き抜いて、1 本目のクラフトをそこで止める。
     *
     * <p>作り手が居なくなるだけなので<b>ジョブは実行中のまま待ちに入る</b>。
     * パターンを戻せば続きから進む。AE2 のジョブ一時停止 API は
     * 1.21.1 の AE2 にしか無いので、両ブランチで同じ手が使えるこちらにしてある。</p>
     */
    private static ItemStack takeButtonPattern(PlotTestHelper helper, BlockPos cpuPos) {
        var cpu = (QuantumCpuBlockEntity) helper.getBlockEntity(cpuPos);
        var patterns = cpu.getLogic().getPatternInv();
        ItemStack pattern = patterns.getStackInSlot(BUTTON_PATTERN_SLOT).copy();
        patterns.setItemDirect(BUTTON_PATTERN_SLOT, ItemStack.EMPTY);
        return pattern;
    }

    /** ネットワークに見えているクラフト CPU の数。 */
    private static int countCpus(PlotTestHelper helper) {
        int cpus = 0;
        for (var ignored : helper.getGrid(BlockPos.ZERO).getCraftingService().getCpus()) {
            cpus++;
        }
        return cpus;
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
                TestCraftingJob job;
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
                state.windowsBefore = QuantumBulkCrafting.bulkWindows;
                state.job = new TestCraftingJob(helper, BlockPos.ZERO,
                        AEItemKey.of(Items.OAK_PLANKS), crafts * planksPerCraft);
            });

            sequence.thenWaitUntil(() -> state.job.tickUntilStarted());
            sequence.thenIdle(20);

            sequence.thenExecute(() -> helper.check(
                    QuantumBulkCrafting.bulkWindows > state.windowsBefore,
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

        final ListCraftingInventory inventory =
                new ListCraftingInventory(what -> {
                });
        final ListCraftingInventory waitingFor =
                new ListCraftingInventory(what -> {
                });
        final ElapsedTimeTracker tracker =
                new ElapsedTimeTracker();

        final IPatternDetails details;
        long remaining;
        boolean removed;
        /** null でなければ Exact (BigInteger) 経路を通す。 */
        AcoBigIntegerJobRegistry.CraftingCursor exactCursor;

        FakeJobView(IPatternDetails details, long remaining) {
            this.details = details;
            this.remaining = remaining;
        }

        @Override
        public ListCraftingInventory getInventory() {
            return inventory;
        }

        @Override
        public ListCraftingInventory getWaitingFor() {
            return waitingFor;
        }

        @Override
        public ElapsedTimeTracker getTimeTracker() {
            return tracker;
        }

        @Override
        public void markDirty() {
        }

        @Override
        public Optional<
                AcoBigIntegerJobRegistry.CraftingCursor>
                exactTasks() {
            return Optional.ofNullable(exactCursor);
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
                public IPatternDetails details() {
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
}
