package jp.main.taikun.insaneae.testplots;

import static jp.main.taikun.insaneae.testplots.TestPlotSupport.storedAmount;
import static jp.main.taikun.insaneae.testplots.TestPlotSupport.ultraCreativeCell;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
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
import appeng.server.testworld.PlotBuilder;
import appeng.server.testworld.PlotTestHelper;
import appeng.server.testworld.TestCraftingJob;
import jp.main.taikun.insaneae.integration.aco.AcoBigIntegerJobRegistry;
import jp.main.taikun.insaneae.provider.InsanePatternProviderBlockEntity;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Blocks;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
//? if >=1.21 {
import appeng.server.testplots.TestPlotClass;
//?}

/**
 * Quantum CPUの検証。対象は、まとめクラフト、内蔵CPU、タスク統合カード、完成待ち帳簿。
 *
 * <p>走らせ方は {@link TestPlotSupport} を参照。</p>
 */
//? if >=1.21 {
@TestPlotClass
//?}
public final class QuantumCpuPlots {

    private QuantumCpuPlots() {
    }

    /**
     * AE2を複製した他ModのクラフトCPUでもまとめ処理が使えることを確かめる
     * Issue #2の回帰テスト。
     *
     * <p>Advanced AEは1.3.6と1.6.12で確認したところ、{@code ExecutingCraftingJob}だけでなく
     * 進捗カウンタ {@code ElapsedTimeTracker} まで自前のコピーで持っている。
     * 「timeTrackerフィールドの型がAE2のtrackerであること」を要求すると、
     * ここで弾かれてまとめ処理が丸ごと使えなくなる。1クラフトずつの遅い経路に落ちる。</p>
     *
     * <p>AAEをdev環境に入れられないので、同じフィールド構造のフェイクCPU
     * ({@link FakeForeignCpuLogic}: job / inventory / tasks / waitingFor /
     * 自前型のtimeTracker / markDirty()) を {@code ReflectiveCraftingJobView} に食わせて、
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

                // 自前型カウンタが直接呼べること。AAEのaddMaxItemsはパッケージプライベート
                var tracker = new ForeignTimeTracker();
                TimeTrackerAdapter.addMaxItems(
                        tracker, 7, AEKeyType.items());
                helper.check(tracker.max == 7,
                        "自前型カウンタへの加算が効いていない: " + tracker.max);

                // AAEと同じフィールド構造のフェイクCPUがレイアウト検査を通ること
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

    /** AAEの自前ElapsedTimeTrackerに相当。addMaxItemsはパッケージプライベート。本物と同じ。 */
    private static final class ForeignTimeTracker {
        long max;

        void addMaxItems(long amount, AEKeyType type) {
            max += amount;
        }
    }

    /** AE2のTaskProgressに相当。longのvalueフィールドだけが要る。 */
    private static final class ForeignTaskProgress {
        long value;

        ForeignTaskProgress(long value) {
            this.value = value;
        }
    }

    /** AAEのExecutingCraftingJobに相当するフィールド構造。 */
    private static final class ForeignExecutingJob {
        final Map<IPatternDetails, ForeignTaskProgress> tasks = new HashMap<>();
        final ListCraftingInventory waitingFor =
                new ListCraftingInventory(what -> {
                });
        final ForeignTimeTracker timeTracker = new ForeignTimeTracker();
    }

    /** AAEのAdvCraftingCPULogicに相当するフィールド構造。 */
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
     * 完成品待ち台帳のBigInteger会計 (PR #3) の回帰テスト。
     *
     * <ol>
     *   <li>longを超える量を積んでも欠けない。クランプも折り返しも無い</li>
     *   <li>NBTの保存 → 読み込みで量が1個もずれない</li>
     *   <li>壊れたエントリ、つまり解決できないキー、負の量、空の量は例外を投げず
     *       そのエントリだけ捨てる — Modを抜いたらチャンクが壊れる、が最悪の後退なので</li>
     *   <li>long形式の旧NBTから移行できる</li>
     *   <li>ネットワークに入り切らないぶんはserverTick後も台帳に正確に残る</li>
     * </ol>
     */
    @TestPlot("insaneae_bigint_pending_outputs")
    public static void bigintPendingOutputs(PlotBuilder plot) {
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("0 0 0");
        plot.blockState("1 0 0", ModBlocks.QUANTUM_CPU.get().defaultBlockState());

        // Long.MAX_VALUE + 5。longのどこにも収まらない代表値
        //? if >=1.21 {
        final java.math.BigInteger overLong =
                java.math.BigInteger.valueOf(Long.MAX_VALUE).add(java.math.BigInteger.valueOf(5));
        //?} else {
        /*final BigInteger overLong =
                BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(5));
        *///?}
        final AEItemKey log = AEItemKey.of(Items.OAK_LOG);

        plot.test(helper -> {
            var sequence = helper.startSequence();

            sequence.thenExecute(() -> {
                var cpu = (QuantumCpuBlockEntity) helper.getBlockEntity(new BlockPos(1, 0, 0));
                //? if >=1.21 {
                var registries = helper.getLevel().registryAccess();
                //?}

                cpu.addPendingOutput(log, overLong);
                helper.check(overLong.equals(cpu.getPendingOutputs().get(log)),
                        "long 超の量が正確に積まれていない: " + cpu.getPendingOutputs().get(log));

                var tag = new CompoundTag();
                //? if >=1.21 {
                cpu.saveAdditional(tag, registries);
                cpu.loadTag(tag, registries);
                //?} else {
                /*cpu.saveAdditional(tag);
                cpu.loadTag(tag);
                *///?}
                helper.check(overLong.equals(cpu.getPendingOutputs().get(log)),
                        "NBT 往復で量がずれた: " + cpu.getPendingOutputs().get(log));

                // 壊れたエントリは例外なしで捨てられ、正常なエントリは残る
                var broken = tag.copy();
                var entries = broken.getCompound("pendingOutputsBig")
                        .getList("entries", Tag.TAG_COMPOUND);
                var badKey = entries.getCompound(0).copy();
                badKey.getCompound("key").putString("id", "nomod:removed_item");
                entries.add(badKey);
                var badAmount = entries.getCompound(0).copy();
                badAmount.putByteArray("amount",
                        //? if >=1.21 {
                        java.math.BigInteger.valueOf(-5).toByteArray());
                        //?} else {
                        /*BigInteger.valueOf(-5).toByteArray());
                        *///?}
                entries.add(badAmount);
                //? if >=1.21 {
                cpu.loadTag(broken, registries); // ここで例外が出たらテストごと落ちる = 検出できる
                //?} else {
                /*cpu.loadTag(broken); // ここで例外が出たらテストごと落ちる = 検出できる
                *///?}
                helper.check(overLong.equals(cpu.getPendingOutputs().get(log)),
                        "壊れたエントリ混在で正常なエントリまで壊れた: " + cpu.getPendingOutputs().get(log));
                // 1.21のAE2は解決できないキーをae2:missing_contentとして保全する
                // 捨てない。負の量のエントリだけが落ち、元の1 + 保全された1 = 2になる
                //? if >=1.21 {
                helper.check(cpu.getPendingOutputs().size() == 2,
                        "壊れたエントリの扱いが想定と違う: " + cpu.getPendingOutputs());
                //?} else {
                /*helper.check(cpu.getPendingOutputs().size() == 1,
                        "壊れたエントリが捨てられていない: " + cpu.getPendingOutputs());
                *///?}

                var legacy = new CompoundTag();
                //? if >=1.21 {
                cpu.saveAdditional(legacy, registries);
                //?} else {
                /*cpu.saveAdditional(legacy);
                *///?}
                legacy.remove("pendingOutputsBig");
                var legacyList = new ListTag();
                //? if >=1.21 {
                legacyList.add(GenericStack.writeTag(registries,
                //?} else {
                /*legacyList.add(GenericStack.writeTag(
                *///?}
                        new GenericStack(log, 123_456_789L)));
                legacy.put("pendingOutputs", legacyList);
                //? if >=1.21 {
                cpu.loadTag(legacy, registries);
                helper.check(java.math.BigInteger.valueOf(123_456_789L)
                //?} else {
                /*cpu.loadTag(legacy);
                helper.check(BigInteger.valueOf(123_456_789L)
                *///?}
                                .equals(cpu.getPendingOutputs().get(log)),
                        "旧形式の移行に失敗: " + cpu.getPendingOutputs().get(log));

                // 後段の確認のため、long超の量へ戻す
                //? if >=1.21 {
                cpu.loadTag(tag, registries);
                //?} else {
                /*cpu.loadTag(tag);
                *///?}
            });

            // serverTickが走る。このネットワークにはストレージが無いので1個も入らない
            sequence.thenIdle(2);

            sequence.thenExecute(() -> {
                var cpu = (QuantumCpuBlockEntity) helper.getBlockEntity(new BlockPos(1, 0, 0));
                var grid = helper.getGrid(BlockPos.ZERO);
                long stored = grid.getStorageService().getInventory()
                        .getAvailableStacks().get(log);
                // 入ったぶん + 台帳の残り = 元の量。1個も消えていない
                //? if >=1.21 {
                var pending = cpu.getPendingOutputs().getOrDefault(log, java.math.BigInteger.ZERO);
                var total = pending.add(java.math.BigInteger.valueOf(stored));
                //?} else {
                /*var pending = cpu.getPendingOutputs().getOrDefault(log, BigInteger.ZERO);
                var total = pending.add(BigInteger.valueOf(stored));
                *///?}
                helper.check(overLong.equals(total),
                        "serverTick 後に量が合わない: 台帳 " + pending + " + ME " + stored);
            });

            sequence.thenSucceed();
        });
    }

    /**
     * まとめクラフトが材料以上に作らないことを確かめる。増殖の回帰テスト。
     *
     * <p>{@code QuantumBulkCrafting.extractInputs} は在庫が足りなければ黙って回数を減らす。
     * その縮小した回数を呼び出し側に返さないと、要求した回数のまま組ませてしまい、
     * 「丸太5本ぶんの材料で256回ぶんの板材ができる」状態になる。
     * 多段クラフトでは中間素材が順次でき上がる = 「残り回数 &gt;&gt; 手元の材料」が通常の進行状態なので、
     * 日常的に通る経路である。</p>
     *
     * <p>ジョブの窓口 ({@link CraftingJobView}) を差し替えられるようにしてあるので、
     * 在庫をこちらで固定して直接呼べる。クラフトCPUを組んで実際にジョブを流すより
     * 決定的で速い。</p>
     */
    @TestPlot("insaneae_quantum_cpu_bulk_conservation")
    public static void quantumCpuBulkConservation(PlotBuilder plot) {
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("[0,2] 0 0");
        plot.blockState("2 0 0", ModBlocks.QUANTUM_CPU.get().defaultBlockState());

        // 丸太1本から板材4枚を作る、シェイプレスのパターン。1回ぶんの材料が1個なので数え違いが起きない
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

            // パターンの読み直しは1 tick遅れ、グリッドのクラフト索引の更新にもう数tickかかる
            sequence.thenIdle(5);

            sequence.thenExecute(() -> {
                var cpu = (QuantumCpuBlockEntity) helper.getBlockEntity(new BlockPos(2, 0, 0));
                var patterns = cpu.getLogic().getAvailablePatterns();
                helper.check(patterns.size() == 1,
                        "Quantum CPU がパターンを 1 枚だけ持っている状態にならなかった: " + patterns.size());

                var grid = helper.getGrid(BlockPos.ZERO);
                state.view = new FakeJobView(patterns.get(0), requested);
                // 5回ぶんしか入れない。要求は1000回
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
     * タスク統合カード: まとめ1回がクラスタ予算を1操作しか消費しないことを確かめる。
     *
     * <p>クラスタ予算3に対して1000回の要求を流す。カード無しなら3回で頭打ちになるところが、
     * カード有りなら1000回まるごと1 tickで通り、消費した操作数は1と報告される
     * 回数の上限はクラスタではなくQuantum CPU自身の予算で、加速カード1枚で65536/tick。</p>
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

            // パターンの読み直しとクラフト索引の更新待ち。bulk_conservationと同じ
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
     * タスク統合カードがBigInteger (Exact) 経路でも効くことを確かめる。
     *
     * <p>{@code insaneae_task_fusion_card} が見ているのは通常のlongタスク経路だけで、
     * ACOの正確な計画を受け取ったときに走る {@code executeExact} は別のループになっている。
     * ここが統合を見ていないと、922京級の注文ほどカードが効かないという逆の症状になる
     * 通常経路では効いているので、気付きにくい。</p>
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

                // Exact台帳はExecutingCraftingJobをキーにするが、ここでは本物のジョブを
                // 立てずに経路だけ試したいので、nullをキーに使う。WeakHashMapはnullを許す
                // このプロットしかnullを使わないが、並列で走る他プロットに残さないよう最後に消す
                AcoBigIntegerJobRegistry.install(null,
                        //? if >=1.21 {
                        Map.of(patterns.get(0), java.math.BigInteger.valueOf(requested)));
                        //?} else {
                        /*Map.of(patterns.get(0), BigInteger.valueOf(requested)));
                        *///?}
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

    /**
     * Quantum CPUの内部スロットに挿したクラフトユニットが、本物のクラフトCPUになるか。
     *
     * <p>ネットワークには他にクラフトCPUを1つも置いていない。
     * そのため「CPU一覧に出る」「発注が通る」「最後まで終わる」がどれも
     * 内蔵CPUのおかげであることが確定する。</p>
     *
     * <p>見ているのは4点。</p>
     * <ol>
     *   <li>ユニットを挿す前はCPUが0個。クラフトストレージが無ければ
     *       CPUとして名乗らない ({@code QuantumCraftingCpu#isFormed})。</li>
     *   <li>挿すとCPUが1個現れ、容量とスレッド数が挿したユニットの合計になる。
     *       ここが0だと {@code CraftingCPUClusterMixin} の数え直しが
     *       内蔵CPU用の経路に入っていない。</li>
     *   <li>2段クラフトが最後まで終わる。ボタン &lt;- 板材 &lt;- 原木 の順。</li>
     *   <li>ユニットを抜くとCPU一覧から消える。</li>
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
                // 2段のツリー: ボタン<- 板材<- 原木。在庫にあるのは原木だけ
                cpu.getLogic().getPatternInv().addItems(
                        CraftingPatternHelper.encodeShapelessCraftingRecipe(helper.getLevel(),
                                new ItemStack(Items.OAK_LOG)));
                cpu.getLogic().getPatternInv().addItems(
                        CraftingPatternHelper.encodeShapelessCraftingRecipe(helper.getLevel(),
                                new ItemStack(Items.OAK_PLANKS)));
            });
            sequence.thenIdle(10);

            sequence.thenExecute(() -> helper.check(countCpus(helper) == 0,
                    "クラフトユニットを挿していないのに CPU が現れている: " + countCpus(helper)
                            + " (isFormed の判定か、他に CPU が置かれている)", cpuPos));

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

            sequence.thenExecute(() -> {
                var cpu = (QuantumCpuBlockEntity) helper.getBlockEntity(cpuPos);
                cpu.getCraftingUnits().clear();
                cpu.getAcceleratorUnits().clear();
                // clear() は1枠ずつの通知を出さないので、数え直しを促す
                cpu.getCraftingCpu().updateUnits();
            });
            sequence.thenIdle(10);
            sequence.thenExecute(() -> helper.check(countCpus(helper) == 0,
                    "ユニットを抜いても CPU 一覧に残っている: " + countCpus(helper), cpuPos));

            sequence.thenSucceed();
        // 既定の持ち時間ではネットワークの起動 + 2段クラフトが終わらない
        // 他のクラフト完走テストと同じ400 tick
        }).maxTicks(400);
    }

    /**
     * 内蔵CPUが容量を切り分けて、2本のクラフトを同時に受けるか。
     *
     * <p>AE2のクラフトCPUは1台1ジョブで、実行中は容量が丸ごと塞がる。
     * Quantum CPUは容量を発注のたびに切り出すので、
     * 1本走らせたまま残量ぶんの発注ができる ({@code QuantumCraftingCpu})。</p>
     *
     * <p>見ているのは4点。</p>
     * <ol>
     *   <li>1本目を始めると、CPU一覧が実行中のぶんと残量ぶんの2行になる。</li>
     *   <li>残量が「合計 - 1本目が要求したバイト数」に減っている
     *       塞がるのは使うぶんだけ。切り分けが効いている。</li>
     *   <li>1本目を止めたまま2本目を発注できる。
     *       ここが通らないと「実行中は発注できない」元の挙動のまま。</li>
     *   <li>両方終わると区画が畳まれ、CPU一覧も容量も元に戻る。</li>
     * </ol>
     *
     * <p>1本目はわざと一時停止して止めてある ({@code setJobSuspended})。
     * そうしないと2本目を出す前に終わってしまい、同時に走っているかを見られない。</p>
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
                // 2段のツリー: ボタン<- 板材<- 原木。在庫にあるのは原木だけ
                cpu.getLogic().getPatternInv().addItems(
                        CraftingPatternHelper.encodeShapelessCraftingRecipe(helper.getLevel(),
                                new ItemStack(Items.OAK_LOG)));
                cpu.getLogic().getPatternInv().addItems(
                        CraftingPatternHelper.encodeShapelessCraftingRecipe(helper.getLevel(),
                                new ItemStack(Items.OAK_PLANKS)));
                // 協調処理ユニットは挿さない。スレッドが0でも区画は切れること
                cpu.getCraftingUnits().addItems(AEBlocks.CRAFTING_STORAGE_64K.stack(storageUnits));
            });
            sequence.thenIdle(10);

            sequence.thenExecute(() -> {
                var cpu = (QuantumCpuBlockEntity) helper.getBlockEntity(cpuPos);
                state.total = cpu.getCraftingCpu().totalStorage();
                helper.check(countCpus(helper) == 1,
                        "発注前なのに CPU が " + countCpus(helper) + " 台ある", cpuPos);
                helper.check(cpu.getCraftingCpu().freeStorage().equals(state.total),
                        "発注前なのに空き容量が合計と違う: "
                                + cpu.getCraftingCpu().freeStorage() + " != " + state.total, cpuPos);
            });

            sequence.thenExecute(() -> state.first = new TestCraftingJob(
                    helper, BlockPos.ZERO, AEItemKey.of(Items.OAK_BUTTON), buttons));
            // 止めるのは始まったのと同じtickのうちに。1 tickでも空けると
            // 先に終わってしまい、同時に走っているところを見られない
            sequence.thenWaitUntil(() -> {
                state.first.tickUntilStarted();
                state.heldPattern = takeButtonPattern(helper, cpuPos);
            });
            // CPU名簿の作り直しはtickの終わりなので、数えるのは少し待ってから
            sequence.thenIdle(5);

            sequence.thenExecute(() -> {
                var cpu = (QuantumCpuBlockEntity) helper.getBlockEntity(cpuPos);
                helper.check(cpu.getCraftingCpu().runningJobs() == 1,
                        "実行中の区画が 1 つになっていない: "
                                + cpu.getCraftingCpu().runningJobs(), cpuPos);
                // 実行中のぶんと残量ぶんで2行
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
        // 2本ぶんのクラフトと、その間の待ちを入れても収まる長さ
        }).maxTicks(600);
    }

    /** ボタンのパターンを入れてある枠。板材のパターンの次に入れているので1番。 */
    private static final int BUTTON_PATTERN_SLOT = 1;

    /**
     * ボタンのパターンを引き抜いて、1本目のクラフトをそこで止める。
     *
     * <p>作り手が居なくなるだけなのでジョブは実行中のまま待ちに入る。
     * パターンを戻せば続きから進む。AE2のジョブ一時停止APIは
     * 1.21.1のAE2にしか無いので、両ブランチで同じ手が使えるこちらにしてある。</p>
     */
    private static ItemStack takeButtonPattern(PlotTestHelper helper, BlockPos cpuPos) {
        var cpu = (QuantumCpuBlockEntity) helper.getBlockEntity(cpuPos);
        var patterns = cpu.getLogic().getPatternInv();
        ItemStack pattern = patterns.getStackInSlot(BUTTON_PATTERN_SLOT).copy();
        patterns.setItemDirect(BUTTON_PATTERN_SLOT, ItemStack.EMPTY);
        return pattern;
    }

    /** ネットワークに見えているクラフトCPUの数。 */
    private static int countCpus(PlotTestHelper helper) {
        int cpus = 0;
        for (var ignored : helper.getGrid(BlockPos.ZERO).getCraftingService().getCpus()) {
            cpus++;
        }
        return cpus;
    }

    /**
     * 実際のジョブでまとめ処理が発火することを確かめる。同居Modに対する回帰テスト。
     *
     * <p>他のまとめ処理テストは {@code QuantumBulkCrafting.execute} を直接呼んでいるので、
     * クラフトCPUのtickから本当に呼ばれているかは見ていない。
     * {@code executeCrafting} の先頭には打ち切り付きで注入しているModが他にもいる
     * AE2 Crafting Optimizerがそうなので、先を越されるとまとめ処理は黙って
     * 素の1回ずつに戻る。結果は同じで遅くなるだけなので、カウンタでしか気付けない。</p>
     *
     * <p>タスク統合カードを挿してあるので、まとめ処理が働いていれば
     * {@code crafts} 回は数tickで終わる。働いていなければ、クラスタ予算の1 tickに数回で
     * 刻まれるため、待ち時間の側でも差が出る。</p>
     */
    @TestPlot("insaneae_bulk_execution_live")
    public static void bulkExecutionLive(PlotBuilder plot) {
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("[0,3] 0 0");
        plot.blockEntity("1 0 0", AEBlocks.DRIVE, drive -> {
            // 材料は無限、完成品の置き場に普通のセルを1枚
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

            // パターンの読み直しとクラフト索引の更新待ち。他のまとめ処理テストと同じ
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
     * 超次元パターンプロバイダーが、加工パターンを1回の呼び出しで何百回ぶんも押し出すこと
     * CurseForgeで報告された、加工パターンが1 tick 5,000回で頭打ちになる件の回帰テスト。
     *
     * <p>材料2種類、丸太1と丸石2のパターンを、別々の面にある2つのチェストへ押し出す。
     * チェストは2種類の材料が同じ空き枠を取り合うので、照会を材料ごとに足し合わせると
     * 入る回数を多く見積もる。見積もりどおり入れると溢れて送信待ちに積まれ、プロバイダが止まる。
     * 押し出しが1巡ごとに見積もりの半分ずつ入れていれば、溢れずに2つとも9割以上埋まる。</p>
     *
     * <p>入りきらなかった回数ぶんの材料はCPUの在庫へ戻ること、完成待ちの帳簿が
     * 押し出した回数と一致することも見る。</p>
     */
    @TestPlot("insaneae_processing_bulk_push")
    public static void processingBulkPush(PlotBuilder plot) {
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("[0,1] 0 0");
        plot.blockState("2 0 0", ModBlocks.INSANE_PATTERN_PROVIDER.get().defaultBlockState());
        // 2つのチェストは隣り合わせない。ラージチェストにならないように
        plot.blockState("2 1 0", Blocks.CHEST.defaultBlockState());
        plot.blockState("2 0 1", Blocks.CHEST.defaultBlockState());

        final long requested = 10_000;
        final int planksPerCraft = 4;
        // チェスト2つで54枠。1回3個、丸太1と丸石2なので、隙間なく詰めて1152回
        final long perfectFit = 54L * 64 / 3;

        plot.test(helper -> {
            var state = new Object() {
                FakeJobView view;
                int pushed;
            };
            var sequence = helper.startSequence();
            BlockPos providerPos = new BlockPos(2, 0, 0);

            sequence.thenExecute(() -> {
                var provider = (InsanePatternProviderBlockEntity) helper.getBlockEntity(providerPos);
                provider.getLogic().getPatternInv().addItems(PatternDetailsHelper.encodeProcessingPattern(
                        //? if >=1.21 {
                        List.of(new GenericStack(AEItemKey.of(Items.OAK_LOG), 1),
                                new GenericStack(AEItemKey.of(Items.COBBLESTONE), 2)),
                        List.of(new GenericStack(AEItemKey.of(Items.OAK_PLANKS), planksPerCraft))));
                        //?} else {
                        /*new GenericStack[] {
                                new GenericStack(AEItemKey.of(Items.OAK_LOG), 1),
                                new GenericStack(AEItemKey.of(Items.COBBLESTONE), 2)},
                        new GenericStack[] {
                                new GenericStack(AEItemKey.of(Items.OAK_PLANKS), planksPerCraft)}));
                        *///?}
            });

            sequence.thenIdle(5);

            sequence.thenExecute(() -> {
                var provider = (InsanePatternProviderBlockEntity) helper.getBlockEntity(providerPos);
                var patterns = provider.getLogic().getAvailablePatterns();
                helper.check(patterns.size() == 1,
                        "超次元パターンプロバイダーがパターンを 1 枚だけ持っている状態にならなかった: "
                                + patterns.size());

                var grid = helper.getGrid(BlockPos.ZERO);
                state.view = new FakeJobView(patterns.get(0), requested);
                state.view.inventory.insert(AEItemKey.of(Items.OAK_LOG), requested, Actionable.MODULATE);
                state.view.inventory.insert(AEItemKey.of(Items.COBBLESTONE), requested * 2,
                        Actionable.MODULATE);

                state.pushed = QuantumBulkCrafting.execute(
                        state.view, (int) requested,
                        (CraftingService) grid.getCraftingService(),
                        grid.getEnergyService(), grid.getPivot().getLevel());
            });

            sequence.thenExecute(() -> {
                var provider = (InsanePatternProviderBlockEntity) helper.getBlockEntity(providerPos);
                long pushed = state.pushed;
                helper.check(pushed >= perfectFit * 9 / 10,
                        "1 回の呼び出しでチェスト 2 つを埋めきれていない: " + pushed + " 回 (詰め切りで "
                                + perfectFit + " 回)");
                helper.check(!provider.getLogic().isBusy(),
                        "送信待ちに材料が積まれた (照会の見積もりどおりに入れて溢れている)");

                long logsInChests = countInChests(helper, Items.OAK_LOG);
                long cobbleInChests = countInChests(helper, Items.COBBLESTONE);
                helper.check(logsInChests == pushed && cobbleInChests == pushed * 2,
                        "チェストの中身が押し出した回数と合わない: 丸太 " + logsInChests + " / 丸石 "
                                + cobbleInChests + " (回数 " + pushed + ")");

                helper.check(state.view.remaining == requested - pushed,
                        "残り回数の引き方が合わない: " + state.view.remaining);
                long logsBack = state.view.inventory.list.get(AEItemKey.of(Items.OAK_LOG));
                long cobbleBack = state.view.inventory.list.get(AEItemKey.of(Items.COBBLESTONE));
                helper.check(logsBack == requested - pushed && cobbleBack == (requested - pushed) * 2,
                        "入らなかった回数ぶんの材料が CPU に戻っていない: 丸太 " + logsBack + " / 丸石 "
                                + cobbleBack);
                long planks = state.view.waitingFor.list.get(AEItemKey.of(Items.OAK_PLANKS));
                helper.check(planks == pushed * planksPerCraft,
                        "完成待ちの数が押し出した回数と釣り合っていない: " + planks);
            });

            sequence.thenSucceed();
        });
    }

    /** プロットの2つのチェストに入っている数の合計。 */
    private static long countInChests(PlotTestHelper helper, Item item) {
        long total = 0;
        for (BlockPos pos : List.of(new BlockPos(2, 1, 0), new BlockPos(2, 0, 1))) {
            var chest = (Container) helper.getBlockEntity(pos);
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                ItemStack stack = chest.getItem(slot);
                if (stack.is(item)) {
                    total += stack.getCount();
                }
            }
        }
        return total;
    }

    /**
     * {@link CraftingJobView} の最小の実装。タスクは1つだけ持つ。
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
        /** nullでなければExact (BigInteger) 経路を通す。 */
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

    /**
     * パターンを1枚変えても、変わっていない枠はデコードし直さないこと (issue #16)。
     *
     * <p>AE2は1枚の出し入れのたびに全枠をデコードし直し、1620枠では1回30msほどかかっていた。
     * 記憶したデコード結果が使われていれば、変えていない枠のパターンは同じオブジェクトのまま残る
     * デコードし直すと、equalsは同じでも別のオブジェクトになる。
     * {@code PatternProviderDecodeCacheMixin} が黙って当たらなくなった場合もここで落ちる。</p>
     */
    @TestPlot("insaneae_pattern_decode_cache")
    public static void patternDecodeCache(PlotBuilder plot) {
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("[0,1] 0 0");
        plot.blockState("2 0 0", ModBlocks.QUANTUM_CPU.get().defaultBlockState());

        var pos = new BlockPos(2, 0, 0);
        var logs = new Item[] {
                Items.OAK_LOG, Items.BIRCH_LOG, Items.SPRUCE_LOG, Items.JUNGLE_LOG};
        var before = new ArrayList<IPatternDetails>();

        plot.test(helper -> {
            var sequence = helper.startSequence();
            sequence.thenExecute(() -> {
                var cpu = (QuantumCpuBlockEntity) helper.getBlockEntity(pos);
                for (var log : logs) {
                    cpu.getLogic().getPatternInv().addItems(
                            CraftingPatternHelper.encodeShapelessCraftingRecipe(helper.getLevel(),
                                    new ItemStack(log)));
                }
            });
            sequence.thenIdle(2);
            sequence.thenExecute(() -> {
                var cpu = (QuantumCpuBlockEntity) helper.getBlockEntity(pos);
                before.addAll(cpu.getLogic().getAvailablePatterns());
                helper.check(before.size() == logs.length, "パターンが " + logs.length + " 枚になっていない");
                // 1枚目だけ別のレシピに差し替える
                var inv = cpu.getLogic().getPatternInv();
                inv.extractItem(0, 1, false);
                inv.insertItem(0, CraftingPatternHelper.encodeShapelessCraftingRecipe(helper.getLevel(),
                        new ItemStack(Items.ACACIA_LOG)), false);
            });
            sequence.thenIdle(2);
            sequence.thenExecute(() -> {
                var cpu = (QuantumCpuBlockEntity) helper.getBlockEntity(pos);
                var after = cpu.getLogic().getAvailablePatterns();
                helper.check(after.size() == logs.length, "差し替え後のパターンが " + after.size() + " 枚");
                helper.check(after.stream().noneMatch(p -> p == before.get(0)), "抜いたパターンが残っている");
                helper.check(after.stream().anyMatch(p -> p.getPrimaryOutput().what()
                                .equals(AEItemKey.of(Items.ACACIA_PLANKS))),
                        "差し替えたパターンが読まれていない");
                for (int i = 1; i < before.size(); i++) {
                    var kept = before.get(i);
                    helper.check(after.stream().anyMatch(p -> p == kept),
                            "変えていない枠までデコードし直している (記憶が使われていない): " + kept);
                }
            });
            sequence.thenSucceed();
        });
    }
}
