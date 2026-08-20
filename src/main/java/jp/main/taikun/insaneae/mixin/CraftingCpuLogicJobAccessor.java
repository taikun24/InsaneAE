package jp.main.taikun.insaneae.mixin;

import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.ExecutingCraftingJob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * クラフト CPU が今抱えている実行中ジョブへの入口。
 *
 * <p>ゲームテストから「そのジョブを ACO の exact 実行が所有しているか」を
 * 直接確かめるために要る ({@code insaneae_aco_exact_ownership})。
 * 注入が走ったかどうかに依存せず、判定そのものを検査したい。</p>
 */
@Mixin(value = CraftingCpuLogic.class, remap = false)
public interface CraftingCpuLogicJobAccessor {

    @Accessor("job")
    ExecutingCraftingJob insaneae$getJob();
}
