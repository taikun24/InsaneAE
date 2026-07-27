package jp.main.taikun.insaneae.mixin;

import appeng.api.networking.energy.IEnergyService;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import jp.main.taikun.insaneae.quantum.QuantumBulkCrafting;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * まとめて処理できるプロバイダ ({@link jp.main.taikun.insaneae.quantum.IBulkCraftingProvider}) 向けの高速経路。
 *
 * <p>AE2 は 1 クラフトごとに {@code pushPattern} を呼ぶので、クラフト数に比例したコストが必ずかかる。
 * 該当するプロバイダがいるときだけ、まとめて処理してその回数を返す。
 * <b>1 回もまとめられなければ何もせず、AE2 本来の処理をそのまま走らせる</b>ので、
 * 分子組立装置など通常の機械の挙動は変わらない。</p>
 *
 * <p>1 tick に何回まとめられるか ({@code maxPatterns}) は AE2 が渡してくるが、
 * その値の計算は int で桁あふれするので {@link CraftingCpuBudgetMixin} が long でやり直している。</p>
 */
@Mixin(value = CraftingCpuLogic.class, remap = false)
public abstract class CraftingCpuLogicMixin {

    @Shadow
    private ExecutingCraftingJob job;

    @Shadow
    @Final
    CraftingCPUCluster cluster;

    @Shadow
    public abstract appeng.crafting.inv.ListCraftingInventory getInventory();

    @Inject(method = "executeCrafting", at = @At("HEAD"), cancellable = true)
    private void insaneae$bulkCrafting(int maxPatterns, CraftingService craftingService,
            IEnergyService energyService, Level level, CallbackInfoReturnable<Integer> cir) {
        int pushed = QuantumBulkCrafting.execute(job, getInventory(), cluster, maxPatterns,
                craftingService, energyService, level);
        if (pushed > 0) {
            cir.setReturnValue(pushed);
        }
    }
}
