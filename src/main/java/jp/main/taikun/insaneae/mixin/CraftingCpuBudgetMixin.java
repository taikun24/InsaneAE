package jp.main.taikun.insaneae.mixin;

import appeng.api.networking.energy.IEnergyService;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import jp.main.taikun.insaneae.crafting.ICoProcessorCount;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * クラフト CPU の <b>1 tick 予算を long で計算し直す</b>。
 * これが無いと巨大な協調処理ユニットでクラフトが 1 回も進まなくなる。
 *
 * <h2>何が壊れているか</h2>
 * <p>AE2 の {@code CraftingCpuLogic#tickCraftingLogic} はこう書かれている:</p>
 * <pre>int remainingOperations = cluster.getCoProcessors() + 1 - (usedOps[0] + usedOps[1] + usedOps[2]);
 *int started = remainingOperations;
 *if (remainingOperations &gt; 0) { ...パターンを送る... }
 *usedOps[2] = usedOps[1]; usedOps[1] = usedOps[0]; usedOps[0] = started - remainingOperations;</pre>
 *
 * <p>「直近 3 tick に使った操作数」を差し引いた残りがその tick の予算、という設計だが
 * <b>全部 int なので巨大なスレッド数だと二重に桁あふれする</b>:</p>
 * <ul>
 *   <li>{@code getCoProcessors()} が {@code Integer.MAX_VALUE} だと {@code +1} で負値になり、
 *       {@code remainingOperations > 0} が永久に false → <b>クラフトが 1 回も進まない</b>。
 *       例外もログも出ないので「クラフトが終わらない」としか見えない。</li>
 *   <li>{@code usedOps[i]} は予算いっぱいまで育つので、3 つの足し算も溢れる。
 *       2^30 だと桁あふれが 2 回起きてたまたま正に戻るため、
 *       <b>「1G ユニットだけは動く」</b>という紛らわしい症状になる。</li>
 * </ul>
 *
 * <h2>直し方</h2>
 * <p>メソッドを丸ごと置き換えると他 Mod と当たるので、<b>枠 (usedOps) を long で持ち直す</b>だけにする:</p>
 * <ol>
 *   <li>{@link #insaneae$tickBudget} が {@code getCoProcessors()} の戻り値を
 *       「long で計算した予算 - 1」にすり替える。</li>
 *   <li>{@link #insaneae$rollUsedOps} が毎 tick 末に AE2 の {@code usedOps} を 0 に均す。</li>
 * </ol>
 * <p>結果、AE2 の式は {@code (予算 - 1) + 1 - 0 = 予算} に退化する。
 * 元の計算式・元の実行フローはそのままに、桁あふれだけが消える。</p>
 *
 * <p>予算の上限が {@code Integer.MAX_VALUE} なのは {@code executeCrafting} の
 * 引数・戻り値が {@code int} だから。ここが最終的な天井 (1 tick に約 21 億操作)。</p>
 *
 * <p>他 Mod が同じメソッドを触っていても起動を止めないよう、
 * <b>{@code insaneae.compat.mixins.json} (required=false) 側</b>に置いてある。
 * 適用されなければ「巨大階層でクラフトが進まない」という元の不具合に戻るだけで、他は動く。</p>
 */
@Mixin(value = CraftingCpuLogic.class, remap = false)
public abstract class CraftingCpuBudgetMixin {

    @Shadow
    @Final
    private int[] usedOps;

    /** 直近 3 tick に使った操作数 (long 版)。AE2 の {@link #usedOps} を置き換えるもの。 */
    @Unique
    private long insaneae$usedOps0;
    @Unique
    private long insaneae$usedOps1;
    @Unique
    private long insaneae$usedOps2;

    /** この tick で予算計算が走ったか (= ジョブ実行の枝に入ったか)。 */
    @Unique
    private boolean insaneae$budgeted;

    /**
     * 1 tick の予算を long で計算し、<b>「予算 - 1」</b>を {@code getCoProcessors()} の戻り値として渡す。
     * 直後に AE2 が {@code + 1 - (usedOps の合計)} を足すが、{@code usedOps} は
     * {@link #insaneae$rollUsedOps} が 0 に均してあるので、結果はちょうど予算になる。
     */
    @Redirect(method = "tickCraftingLogic",
            at = @At(value = "INVOKE",
                    target = "Lappeng/me/cluster/implementations/CraftingCPUCluster;getCoProcessors()I"))
    private int insaneae$tickBudget(CraftingCPUCluster cluster) {
        insaneae$budgeted = true;

        // CraftingCPUCluster は final クラスなので、Mixin でインタフェースを足したことを
        // コンパイラは知らない。Object 経由で確かめる。
        long coProcessors = (Object) cluster instanceof ICoProcessorCount counted
                ? counted.insaneae$coProcessorCount()
                : cluster.getCoProcessors();

        // ここが long なので、スレッド数がいくつでも溢れない。
        long budget = coProcessors + 1 - (insaneae$usedOps0 + insaneae$usedOps1 + insaneae$usedOps2);
        // 使い切っている (0 以下) ときは -1 を返し、AE2 の +1 で 0 にして実行させない。
        long clamped = Math.max(0, Math.min(budget, Integer.MAX_VALUE));
        return (int) clamped - 1;
    }

    /**
     * long 版の枠を 1 つずらし、AE2 の int 配列を 0 に戻す。
     *
     * <p>AE2 はこのメソッドの最後に {@code usedOps[0] = started - remainingOperations}
     * (= この tick に実際に送ったパターン数) を書いているので、それを long 側へ移して片付ける。</p>
     */
    @Inject(method = "tickCraftingLogic", at = @At("RETURN"))
    private void insaneae$rollUsedOps(IEnergyService energyService, CraftingService craftingService,
            CallbackInfo ci) {
        if (!insaneae$budgeted) {
            // ジョブが無い / 中断された tick。AE2 も usedOps を触らないので枠もずらさない。
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
