package jp.main.taikun.insaneae.testplots;

import static jp.main.taikun.insaneae.testplots.TestPlotSupport.optionalBlock;
import static jp.main.taikun.insaneae.testplots.TestPlotSupport.storedAmount;
import static jp.main.taikun.insaneae.testplots.TestPlotSupport.ultraCreativeCell;

import appeng.api.stacks.AEItemKey;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.server.testplots.CraftingPatternHelper;
import appeng.server.testplots.TestPlot;
import appeng.server.testplots.TestPlotClass;
import appeng.server.testworld.PlotBuilder;
import appeng.server.testworld.TestCraftingJob;
import jp.main.taikun.insaneae.compat.AaeCompatCounters;
import jp.main.taikun.insaneae.quantum.QuantumCpuBlockEntity;
import jp.main.taikun.insaneae.registries.ModBlocks;
import jp.main.taikun.insaneae.registries.ModUpgrades;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Advanced AE のクラフト CPU へ入れた互換 Mixin が実際に走るか。
 *
 * <p>走らせ方は {@link TestPlotSupport} を参照。</p>
 */
@TestPlotClass
public final class AdvancedAePlots {

    private AdvancedAePlots() {
    }

    /**
     * Advanced AE のクラフト CPU へ入れている compat Mixin が<b>実際に当たっている</b>ことを確かめる。
     *
     * <p>{@code @Pseudo} + {@code targets} の名指しは、相手のクラス名が変わっても
     * <b>エラーにならず黙って当たらなくなる</b> ({@code required=false} なので尚更)。
     * 症状は「Advanced AE のクラフト CPU だけ遅い」「巨大な協調処理数だと途中で止まる」で、
     * どちらもログに何も出ないため、当たっているかどうかはここで見張るしかない。</p>
     *
     * <p>Advanced AE が入っていない環境では<b>何も検査せずに成功する</b>
     * (通常のゲームテストは Advanced AE 無しで回るため)。
     * 相手のクラスは普段ロードされないので、{@code Class.forName} で明示的に読み込んで
     * Mixin の変換を走らせてから、注入したメソッドが生えているかを見る。</p>
     */
    @TestPlot("insaneae_aae_cpu_mixins")
    public static void advancedAeCpuMixins(PlotBuilder plot) {
        // 中身は反射で見るだけだが、空のプロットは AE2 の Plot#getBounds が通らないので 1 つ置く。
        plot.cable("0 0 0");
        plot.test(helper -> helper.startSequence().thenExecute(() -> {
            if (!ModList.get().isLoaded("advanced_ae")) {
                return;
            }
            Class<?> logic;
            try {
                logic = Class.forName("net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic");
            } catch (ClassNotFoundException missing) {
                throw new GameTestAssertException(
                        "Advanced AE は居るのに AdvCraftingCPULogic が無い。"
                                + "クラス名が変わったので compat Mixin の targets を直すこと: " + missing);
            }
            // Mixin は注入ハンドラを handler$<hash>$<元の名前> / redirect$... に改名して混ぜるので、
            // 名前の一致ではなく<b>末尾</b>で見る。@Unique のメソッドだけは元の名前のまま入る。
            Set<String> injected = new HashSet<>();
            for (var method : logic.getDeclaredMethods()) {
                if (method.getName().contains("insaneae$")) {
                    injected.add(method.getName());
                }
            }
            // まとめ処理 (AdvCraftingCpuLogicMixin) と 1 tick 予算の long 化 (AdvCraftingCpuBudgetMixin)。
            for (String expected : List.of("insaneae$bulkCrafting", "insaneae$reduceBudget",
                    "insaneae$addBulkToResult", "insaneae$tickBudget", "insaneae$rollUsedOps")) {
                helper.check(injected.stream().anyMatch(name -> name.endsWith(expected)),
                        "Advanced AE の CPU へ " + expected + " が注入されていない (当たったのは "
                                + injected + ")");
            }

            // クラスタ側の容量の飽和 (AdvCraftingCpuStorageMixin)。当たっていないと
            // InsaneAE のクラフトストレージを数個積んだだけで容量が負に折り返す。
            Class<?> cluster;
            try {
                cluster = Class.forName("net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPUCluster");
            } catch (ClassNotFoundException missing) {
                throw new GameTestAssertException(
                        "Advanced AE は居るのに AdvCraftingCPUCluster が無い。"
                                + "クラス名が変わったので compat Mixin の targets を直すこと: " + missing);
            }
            Set<String> clusterInjected = new HashSet<>();
            for (var method : cluster.getDeclaredMethods()) {
                if (method.getName().contains("insaneae$")) {
                    clusterInjected.add(method.getName());
                }
            }
            for (String expected : List.of("insaneae$saturateStorageBytes",
                    "insaneae$saturateStorageMultiplier")) {
                helper.check(clusterInjected.stream().anyMatch(name -> name.endsWith(expected)),
                        "Advanced AE のクラスタへ " + expected + " が注入されていない (当たったのは "
                                + clusterInjected + ")");
            }
        }).thenSucceed());
    }

    /**
     * <b>Advanced AE のクラフト CPU に載せたジョブが、Quantum CPU 経由で最後まで終わるか。</b>
     *
     * <p>{@link #craftFromCreativeCell} と同じ内容を、クラフト CPU だけ
     * Advanced AE の量子コンピュータに差し替えたもの。報告されている
     * 「進行中のままタスクが空で終わらない」は<b>この並び</b>で起きている。</p>
     *
     * <p>Advanced AE が入っていない環境では<b>何も検査せずに成功する</b>
     * ({@code ./gradlew runGameTestServer -PwithAdvancedAe=true -PwithAco} で有効になる)。</p>
     */
    @TestPlot("insaneae_craft_on_advanced_ae_cpu")
    public static void craftOnAdvancedAeCpu(PlotBuilder plot) {
        var core = aaeBlock("quantum_core");
        var unit = aaeBlock("quantum_storage_256");
        var shell = aaeBlock("quantum_structure");
        if (core == null || unit == null || shell == null) {
            // 空のプロットは AE2 の Plot#getBounds が通らないので 1 つ置く。
            plot.cable("0 0 0");
            plot.test(helper -> helper.startSequence().thenSucceed());
            return;
        }

        plot.creativeEnergyCell("0 -1 0");
        plot.cable("[0,4] 0 0");
        plot.blockEntity("1 0 0", AEBlocks.DRIVE, drive -> {
            drive.getInternalInventory().addItems(ultraCreativeCell(Items.OAK_LOG));
            drive.getInternalInventory().addItems(AEItems.ITEM_CELL_64K.stack());
        });
        plot.blockState("3 0 0", ModBlocks.QUANTUM_CPU.get().defaultBlockState());

        // Advanced AE の量子コンピュータは<b>中空の箱</b>で、外殻が quantum_structure、
        // 中身が機能ブロックという構造 (AdvCraftingCPUCalculator#verifyInternalStructure)。
        // 5..7 x 0..3 x 0..2 の箱にすると、内側はちょうど (6,1,1) と (6,2,1) の 2 マス。
        plot.blockState("[5,7] [0,3] [0,2]", shell.defaultBlockState());
        plot.blockState("6 1 1", core.defaultBlockState());
        plot.blockState("6 2 1", unit.defaultBlockState());

        final long requested = 64;

        plot.test(helper -> {
            var state = new Object() {
                TestCraftingJob job;
            };
            var sequence = helper.startSequence();

            sequence.thenExecute(() -> {
                var cpu = (QuantumCpuBlockEntity) helper.getBlockEntity(new BlockPos(3, 0, 0));
                // 2 段のツリー: ボタン <- 板材 <- 原木。実環境の 8^N 連鎖に形を寄せてある
                // (在庫にあるのは原木だけなので、途中段も必ずクラフトされる)。
                cpu.getLogic().getPatternInv().addItems(
                        CraftingPatternHelper.encodeShapelessCraftingRecipe(helper.getLevel(),
                                new ItemStack(Items.OAK_LOG)));
                cpu.getLogic().getPatternInv().addItems(
                        CraftingPatternHelper.encodeShapelessCraftingRecipe(helper.getLevel(),
                                new ItemStack(Items.OAK_PLANKS)));
                // 実環境と同じカード構成 (加速 7 + タスク統合 1)。
                for (int i = 0; i < QuantumCpuBlockEntity.MAX_ACCELERATION_CARDS; i++) {
                    cpu.getUpgrades().addItems(
                            new ItemStack(ModUpgrades.QUANTUM_ACCELERATION_CARD.get()));
                }
                cpu.getUpgrades().addItems(new ItemStack(ModUpgrades.TASK_FUSION_CARD.get()));
            });
            // 多ブロック構造が組み上がるまで少し待つ。
            sequence.thenIdle(20);

            // 先に CPU が組めているか見る。組めていないと「failed to submit job」としか出ず、
            // 構造の問題なのか実行の問題なのか区別がつかない。
            sequence.thenExecute(() -> {
                int cpus = 0;
                for (var ignored : helper.getGrid(BlockPos.ZERO).getCraftingService().getCpus()) {
                    cpus++;
                }
                helper.check(cpus > 0, "Advanced AE の量子コンピュータが組み上がっていない "
                        + "(外殻・中身の並びか、必要ブロックが変わった可能性)");
            });

            sequence.thenExecute(() -> state.job = new TestCraftingJob(
                    helper, BlockPos.ZERO, AEItemKey.of(Items.OAK_BUTTON), requested));
            sequence.thenWaitUntil(() -> state.job.tickUntilStarted());

            sequence.thenWaitUntil(() -> {
                long stored = storedAmount(helper, Items.OAK_BUTTON);
                if (stored < requested) {
                    throw new GameTestAssertException("Advanced AE の CPU で 2 段クラフトが "
                            + stored + "/" + requested + " しか進まない");
                }
            });
            sequence.thenWaitUntil(() -> {
                for (var cpu : helper.getGrid(BlockPos.ZERO).getCraftingService().getCpus()) {
                    if (cpu.isBusy()) {
                        throw new GameTestAssertException(
                                "完成品は揃ったのに Advanced AE の CPU がジョブを抱えたまま "
                                        + "(完成待ちが減っていない)");
                    }
                }
            });

            // compat Mixin が<b>実際に走った</b>こと。名前が生えているかを見る検査では
            // 足りない (Mixin はメソッドを混ぜてから injector を配線するので、
            // 配線に失敗してもメソッドだけは生える)。
            sequence.thenExecute(() -> {
                helper.check(AaeCompatCounters.STORAGE_SATURATIONS.get() > 0,
                        "AdvCraftingCpuStorageMixin が一度も走っていない "
                                + "(@Redirect の配線に失敗している可能性 — ログの "
                                + "InvalidInjectionException を確認すること)");
                helper.check(AaeCompatCounters.BUDGET_CALCULATIONS.get() > 0,
                        "AdvCraftingCpuBudgetMixin が一度も走っていない (同上)");
            });
            sequence.thenSucceed();
        });
    }

    /** Advanced AE のブロック。入っていなければ null。 */
    @Nullable
    private static Block aaeBlock(String id) {
        return optionalBlock("advanced_ae", id);
    }
}
