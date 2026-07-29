package jp.main.taikun.insaneae.mixin;

import appeng.api.stacks.AEKey;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.CraftingTreeProcess;
import appeng.crafting.inv.CraftingSimulationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

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
}
