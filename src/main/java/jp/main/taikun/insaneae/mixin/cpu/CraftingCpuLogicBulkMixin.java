package jp.main.taikun.insaneae.mixin.cpu;

import appeng.api.networking.energy.IEnergyService;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import jp.main.taikun.insaneae.quantum.Ae2CraftingJobView;
import jp.main.taikun.insaneae.quantum.BulkCraftingHook;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * まとめて処理できるプロバイダ ({@link jp.main.taikun.insaneae.quantum.IBulkCraftingProvider}) 向けの高速経路。
 *
 * <p>AE2は1クラフトごとに {@code pushPattern} を呼ぶので、クラフト数に比例したコストが必ずかかる。
 * 該当するプロバイダがいるときだけ、まとめて処理してその回数を返す。
 * 1回もまとめられなければ何もせず、AE2本来の処理をそのまま走らせるので、
 * 分子組立装置など通常の機械の挙動は変わらない。</p>
 *
 * <p>1 tickに何回まとめられるか ({@code maxPatterns}) はAE2が渡してくるが、
 * その値の計算はintで桁あふれするので {@link CraftingCpuBudgetMixin} がlongでやり直している。</p>
 *
 * <h2>priorityを800にしてある理由</h2>
 * <p>ACO 2.0.1の {@code CraftingCpuLogicCountedTransferMixin} (priority 900) も
 * {@code executeCrafting} の先頭に打ち切り付きで注入している。あちらは
 * 「材料がCPUの在庫に無いタスクしか無い」と0を返してそのtickを打ち切るが、
 * ACOから委譲されたBigIntegerジョブは材料を窓ごとにMEから直接取り出すので
 * CPUの在庫は常に空で、毎tick打ち切られて一度も進まない。
 * 先に注入された方が先に走るので、こちらを先にしておく。
 * 委譲されたジョブはこちらが打ち切り、それ以外は残りの予算でACOの処理へ流れる。</p>
 */
@Mixin(value = CraftingCpuLogic.class, remap = false, priority = 800)
public abstract class CraftingCpuLogicBulkMixin {

    @Shadow
    private ExecutingCraftingJob job;

    @Shadow
    @Final
    CraftingCPUCluster cluster;

    @Shadow
    public abstract ListCraftingInventory getInventory();

    /**
     * まとめ処理の帳簿。3つの注入で共有する。
     * 予算の引き算と戻り値の足し戻しは {@link BulkCraftingHook} 側で清算される。
     */
    @Unique
    private BulkCraftingHook insaneae$hook;

    /** Mixinのフィールド初期化子に頼らず、使うときに作る。 */
    @Unique
    private BulkCraftingHook insaneae$hook() {
        if (insaneae$hook == null) {
            insaneae$hook = new BulkCraftingHook();
        }
        return insaneae$hook;
    }

    /**
     * まとめて処理できるぶんを先に片付ける。
     *
     * <p>ここでAE2の処理を打ち切ってはいけない。
     * {@code executeCrafting} は「そのtickにジョブ内の全パターンを各プロバイダへ押し出す」
     * 唯一のループなので、打ち切ると同じジョブに含まれる他のパターン
     * 機械に投げる加工パターンなどが、そのtickはまったく処理されない。
     * Quantum CPUの予算は毎tick補充されるため、Quantum CPUに仕事がある限り
     * 他のプロバイダが永久に飢える。</p>
     *
     * <p>そこで、予算を使い切ったときだけ打ち切り、そうでなければ
     * 残りの予算でAE2の処理を続行させる。</p>
     */
    @Inject(method = "executeCrafting", at = @At("HEAD"), cancellable = true)
    private void insaneae$bulkCrafting(int maxPatterns, CraftingService craftingService,
            IEnergyService energyService, Level level, CallbackInfoReturnable<Integer> cir) {
        if (cir.isCancelled()) {
            return;
        }
        int finished = insaneae$hook().begin(Ae2CraftingJobView.of(job, getInventory(), cluster),
                maxPatterns, craftingService, energyService, level);
        if (finished >= 0) {
            cir.setReturnValue(finished);
        }
    }

    /**
     * まとめ処理で使ったぶんをAE2の予算から引く。
     *
     * <p>この注入が適用されなかった場合。順序の都合で{@link #insaneae$bulkCrafting}より先に走った場合を含む
     * は0が引かれるだけで、そのtickの操作数がまとめ処理のぶんだけ多くなるにとどまる。</p>
     */
    @ModifyVariable(method = "executeCrafting", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private int insaneae$reduceBudget(int maxPatterns) {
        return insaneae$hook().reduce(maxPatterns);
    }

    /** まとめ処理で押し出した回数を戻り値に足し戻し、印を消す。 */
    @Inject(method = "executeCrafting", at = @At("RETURN"), cancellable = true)
    private void insaneae$addBulkToResult(int maxPatterns, CraftingService craftingService,
            IEnergyService energyService, Level level, CallbackInfoReturnable<Integer> cir) {
        int bulk = insaneae$hook().takePushed();
        if (bulk > 0) {
            cir.setReturnValue(cir.getReturnValue() + bulk);
        }
    }
}
