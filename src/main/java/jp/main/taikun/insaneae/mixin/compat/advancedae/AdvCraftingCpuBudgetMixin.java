package jp.main.taikun.insaneae.mixin.compat.advancedae;

import appeng.api.networking.energy.IEnergyService;
import appeng.me.service.CraftingService;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import jp.main.taikun.insaneae.integration.advancedae.AaeCompatCounters;

/**
 * Advanced AEのクラフトCPUにも、1 tick予算のlong化を入れる。
 *
 * <p>{@link jp.main.taikun.insaneae.mixin.cpu.CraftingCpuBudgetMixin} とまったく同じ問題で、
 * 直し方も同じ。Advanced AEはAE2のクラフトCPUを使わず {@code AdvCraftingCPULogic} という
 * 自前の複製を持っており、{@code tickCraftingLogic} の予算計算も
 * {@code getCoProcessors() + 1 - (usedOps[0] + usedOps[1] + usedOps[2])} を
 * 全部intでやっている複製になっている。</p>
 *
 * <h2>いつ表に出るか</h2>
 * <p>Advanced Quantum EngineeringのBigInteger量子コアは、協調処理数の既定値が
 * {@code Integer.MAX_VALUE - 1}、つまり約21億になっている。{@code getCoProcessors()}自体は
 * 飽和させてあるので初回のtickは通るが、{@code usedOps} は使った操作数まで育つため、
 * 2 tick目以降は3つの足し算のほうがintから溢れて予算が負に落ちる。
 * すると {@code remainingOperations > 0} がfalseになり、
 * 「クラフトは始まるのに進まない・途中で止まる」という症状になる。
 * 例外もログも出ないので、原因がCPU側の桁あふれだとは分からない。</p>
 *
 * <h2>直し方</h2>
 * <p>AE2版と同じ2段構え。{@code getCoProcessors()} の戻り値を「longで計算した予算 - 1」に
 * すり替え、tickの最後にAE2側の {@code usedOps} を0に均す。
 * 結果、元の式は {@code (予算 - 1) + 1 - 0 = 予算} に退化し、
 * 計算式も実行フローも変えずに桁あふれだけが消える。</p>
 *
 * <p>{@link Pseudo} 付きなのでAdvanced AEが入っていなければ黙って読み飛ばされる。
 * 登録先が {@code required=false} なのもAE2版と同じで、当たらなければ
 * 「巨大な協調処理数で止まる」という元の不具合に戻るだけ。</p>
 */
@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic", remap = false)
public abstract class AdvCraftingCpuBudgetMixin {

    @Shadow
    @Final
    private int[] usedOps;

    /** 直近3 tickに使った操作数。long版で、Advanced AEの{@link #usedOps}を置き換えるもの。 */
    @Unique
    private long insaneae$usedOps0;
    @Unique
    private long insaneae$usedOps1;
    @Unique
    private long insaneae$usedOps2;

    /** このtickで予算計算が走ったか。= ジョブ実行の枝に入ったか。 */
    @Unique
    private boolean insaneae$budgeted;

    @Redirect(method = "tickCraftingLogic",
            at = @At(value = "INVOKE",
                    target = "Lnet/pedroksl/advanced_ae/common/cluster/AdvCraftingCPU;"
                            + "getCoProcessors()I"),
            require = 0)
    private int insaneae$tickBudget(@Coerce Object cluster) {
        AaeCompatCounters.BUDGET_CALCULATIONS.incrementAndGet();
        insaneae$budgeted = true;

        long coProcessors = insaneae$coProcessors(cluster);
        // ここがlongなので、協調処理数がいくつでも溢れない
        long budget = coProcessors + 1 - (insaneae$usedOps0 + insaneae$usedOps1 + insaneae$usedOps2);
        // 使い切っている、つまり0以下のときは -1を返し、+1で0にして実行させない
        long clamped = Math.max(0, Math.min(budget, Integer.MAX_VALUE));
        return (int) clamped - 1;
    }

    /**
     * Advanced AEのクラスは名指しできないので反射で読む。
     * 読めなければ0として扱い、元の挙動より悪くしない。そのtickは実行しない。
     */
    @Unique
    private long insaneae$coProcessors(Object cluster) {
        if (cluster == null) {
            return 0L;
        }
        try {
            return ((Number) cluster.getClass().getMethod("getCoProcessors").invoke(cluster))
                    .longValue();
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return 0L;
        }
    }

    /** long版の枠を1つずらし、Advanced AEのint配列を0に戻す。 */
    @Inject(method = "tickCraftingLogic", at = @At("RETURN"), require = 0)
    private void insaneae$rollUsedOps(IEnergyService energyService, CraftingService craftingService,
            CallbackInfo ci) {
        if (!insaneae$budgeted) {
            // ジョブが無い / 中断されたtick。向こうもusedOpsを触らないので枠もずらさない
            return;
        }
        insaneae$budgeted = false;

        insaneae$usedOps2 = insaneae$usedOps1;
        insaneae$usedOps1 = insaneae$usedOps0;
        insaneae$usedOps0 = usedOps[0];

        usedOps[0] = 0;
        usedOps[1] = 0;
        usedOps[2] = 0;
    }
}
