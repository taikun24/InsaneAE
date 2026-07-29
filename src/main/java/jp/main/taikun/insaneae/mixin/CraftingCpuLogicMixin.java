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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
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

    /**
     * この tick にまとめ処理で押し出した回数。AE2 の処理を通した後で戻り値に足し戻す。
     * 常に {@link #insaneae$addBulkToResult} で 0 に戻すので、tick をまたいで残らない。
     */
    @Unique
    private int insaneae$bulkPushed;

    /**
     * まとめて処理できるぶんを先に片付ける。
     *
     * <p><b>ここで AE2 の処理を打ち切ってはいけない。</b>
     * {@code executeCrafting} は「その tick にジョブ内の全パターンを各プロバイダへ押し出す」
     * 唯一のループなので、打ち切ると<b>同じジョブに含まれる他のパターン
     * (機械に投げる加工パターンなど) がその tick はまったく処理されない</b>。
     * Quantum CPU の予算は毎 tick 補充されるため、Quantum CPU に仕事がある限り
     * 他のプロバイダが永久に飢える (1.0.2 までの不具合)。</p>
     *
     * <p>そこで、予算を使い切ったときだけ打ち切り、そうでなければ
     * 残りの予算で AE2 の処理を続行させる。</p>
     */
    @Inject(method = "executeCrafting", at = @At("HEAD"), cancellable = true)
    private void insaneae$bulkCrafting(int maxPatterns, CraftingService craftingService,
            IEnergyService energyService, Level level, CallbackInfoReturnable<Integer> cir) {
        int pushed = QuantumBulkCrafting.execute(job, getInventory(), cluster, maxPatterns,
                craftingService, energyService, level);
        if (pushed <= 0) {
            return;
        }
        if (pushed >= maxPatterns) {
            // 予算を使い切ったので、この tick に AE2 がやることはもう無い。
            cir.setReturnValue(pushed);
            return;
        }
        insaneae$bulkPushed = pushed;
    }

    /**
     * まとめ処理で使ったぶんを AE2 の予算から引く。
     *
     * <p>この注入が効かなかった場合 (順序の都合で {@link #insaneae$bulkCrafting} より先に走った場合を含む)
     * は 0 が引かれるだけで、その tick の操作数がまとめ処理のぶんだけ多くなるにとどまる。</p>
     */
    @ModifyVariable(method = "executeCrafting", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private int insaneae$reduceBudget(int maxPatterns) {
        return maxPatterns - insaneae$bulkPushed;
    }

    /** まとめ処理で押し出した回数を戻り値に足し戻し、印を消す。 */
    @Inject(method = "executeCrafting", at = @At("RETURN"), cancellable = true)
    private void insaneae$addBulkToResult(int maxPatterns, CraftingService craftingService,
            IEnergyService energyService, Level level, CallbackInfoReturnable<Integer> cir) {
        if (insaneae$bulkPushed > 0) {
            cir.setReturnValue(cir.getReturnValue() + insaneae$bulkPushed);
            insaneae$bulkPushed = 0;
        }
    }
}

