package jp.main.taikun.insaneae.mixin.compat;

import appeng.api.networking.energy.IEnergyService;
import appeng.me.service.CraftingService;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Advanced AE のクラフト CPU にも、<b>1 tick 予算の long 化</b>を入れる。
 *
 * <p>{@link jp.main.taikun.insaneae.mixin.CraftingCpuBudgetMixin} と<b>まったく同じ問題</b>で、
 * 直し方も同じ。Advanced AE は AE2 のクラフト CPU を使わず {@code AdvCraftingCPULogic} という
 * 自前の複製を持っており、{@code tickCraftingLogic} の予算計算も
 * {@code getCoProcessors() + 1 - (usedOps[0] + usedOps[1] + usedOps[2])} を
 * <b>全部 int で</b>やっている複製になっている。</p>
 *
 * <h2>いつ表に出るか</h2>
 * <p>Advanced Quantum Engineering の BigInteger 量子コアは、協調処理数の既定値が
 * {@code Integer.MAX_VALUE - 1} (約 21 億) になっている。{@code getCoProcessors()} 自体は
 * 飽和させてあるので初回の tick は通るが、<b>{@code usedOps} は使った操作数まで育つ</b>ため、
 * 2 tick 目以降は 3 つの足し算のほうが int から溢れて予算が負に落ちる。
 * すると {@code remainingOperations > 0} が false になり、
 * <b>「クラフトは始まるのに進まない・途中で止まる」</b>という症状になる。
 * 例外もログも出ないので、原因が CPU 側の桁あふれだとは分からない。</p>
 *
 * <h2>直し方</h2>
 * <p>AE2 版と同じ 2 段構え。{@code getCoProcessors()} の戻り値を「long で計算した予算 - 1」に
 * すり替え、tick の最後に AE2 側の {@code usedOps} を 0 に均す。
 * 結果、元の式は {@code (予算 - 1) + 1 - 0 = 予算} に退化し、
 * <b>計算式も実行フローも変えずに桁あふれだけが消える</b>。</p>
 *
 * <p>{@link Pseudo} 付きなので Advanced AE が入っていなければ黙って読み飛ばされる。
 * 登録先が {@code required=false} なのも AE2 版と同じで、当たらなければ
 * 「巨大な協調処理数で止まる」という元の不具合に戻るだけ。</p>
 */
@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic", remap = false)
public abstract class AdvCraftingCpuBudgetMixin {

    @Shadow
    @Final
    private int[] usedOps;

    /** 直近 3 tick に使った操作数 (long 版)。Advanced AE の {@link #usedOps} を置き換えるもの。 */
    @Unique
    private long insaneae$usedOps0;
    @Unique
    private long insaneae$usedOps1;
    @Unique
    private long insaneae$usedOps2;

    /** この tick で予算計算が走ったか (= ジョブ実行の枝に入ったか)。 */
    @Unique
    private boolean insaneae$budgeted;

    @Redirect(method = "tickCraftingLogic",
            at = @At(value = "INVOKE",
                    target = "Lnet/pedroksl/advanced_ae/common/cluster/AdvCraftingCPU;"
                            + "getCoProcessors()I"),
            require = 0)
    private int insaneae$tickBudget(Object cluster) {
        insaneae$budgeted = true;

        long coProcessors = insaneae$coProcessors(cluster);
        // ここが long なので、協調処理数がいくつでも溢れない。
        long budget = coProcessors + 1 - (insaneae$usedOps0 + insaneae$usedOps1 + insaneae$usedOps2);
        // 使い切っている (0 以下) ときは -1 を返し、+1 で 0 にして実行させない。
        long clamped = Math.max(0, Math.min(budget, Integer.MAX_VALUE));
        return (int) clamped - 1;
    }

    /**
     * Advanced AE のクラスは名指しできないので反射で読む。
     * 読めなければ 0 として扱い、<b>元の挙動より悪くしない</b> (その tick は実行しない)。
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

    /** long 版の枠を 1 つずらし、Advanced AE の int 配列を 0 に戻す。 */
    @Inject(method = "tickCraftingLogic", at = @At("RETURN"), require = 0)
    private void insaneae$rollUsedOps(IEnergyService energyService, CraftingService craftingService,
            CallbackInfo ci) {
        if (!insaneae$budgeted) {
            // ジョブが無い / 中断された tick。向こうも usedOps を触らないので枠もずらさない。
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
