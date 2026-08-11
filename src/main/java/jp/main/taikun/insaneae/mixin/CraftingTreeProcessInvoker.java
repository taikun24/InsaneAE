package jp.main.taikun.insaneae.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;
import appeng.crafting.inv.CraftingSimulationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

/**
 * {@link CraftingTreeProcess} のパッケージプライベートなメソッドを外から呼ぶための窓口。
 * コードは注入せず、呼び出しの入口を作るだけ。
 *
 * @see jp.main.taikun.insaneae.crafting.CraftingCalculationBatch
 */
@Mixin(value = CraftingTreeProcess.class, remap = false)
public interface CraftingTreeProcessInvoker {

    /** このパターンを {@code times} 回ぶんシミュレートする。 */
    @Invoker("request")
    void insaneae$request(CraftingSimulationState inv, long times)
            throws CraftBranchFailure, InterruptedException;

    /** 1 回のクラフトで {@code what} がいくつ出るか。 */
    @Invoker("getOutputCount")
    long insaneae$getOutputCount(AEKey what);

    /** 材料の子ノードと 1 クラフトあたりの必要数。long あふれの門番が最大値を調べるのに使う。 */
    @Accessor("nodes")
    Map<CraftingTreeNode, Long> insaneae$getNodes();

    /** このプロセスのパターン (出力側の 1 クラフトあたりの個数を調べるのに使う)。 */
    @Accessor("details")
    IPatternDetails insaneae$getDetails();
}
