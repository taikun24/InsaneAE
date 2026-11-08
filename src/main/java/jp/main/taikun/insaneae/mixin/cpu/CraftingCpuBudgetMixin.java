package jp.main.taikun.insaneae.mixin.cpu;

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
 * クラフトCPUの1 tick予算をlongで計算し直す。
 * これが無いと巨大な協調処理ユニットでクラフトが1回も進まなくなる。
 *
 * <h2>何が壊れているか</h2>
 * <p>AE2の {@code CraftingCpuLogic#tickCraftingLogic} はこう書かれている:</p>
 * <pre>int remainingOperations = cluster.getCoProcessors() + 1 - (usedOps[0] + usedOps[1] + usedOps[2]);
 *int started = remainingOperations;
 *if (remainingOperations &gt; 0) { ...パターンを送る... }
 *usedOps[2] = usedOps[1]; usedOps[1] = usedOps[0]; usedOps[0] = started - remainingOperations;</pre>
 *
 * <p>「直近3 tickに使った操作数」を差し引いた残りがそのtickの予算、という設計だが
 * 全部intなので巨大なスレッド数だと二重に桁あふれする:</p>
 * <ul>
 *   <li>{@code getCoProcessors()} が {@code Integer.MAX_VALUE} だと {@code +1} で負値になり、
 *       {@code remainingOperations > 0} が永久にfalse → クラフトが1回も進まない。
 *       例外もログも出ないので「クラフトが終わらない」としか見えない。</li>
 *   <li>{@code usedOps[i]} は予算いっぱいまで育つので、3つの足し算も溢れる。
 *       2^30だと桁あふれが2回起きてたまたま正に戻るため、
 *       「1Gユニットだけは動く」という紛らわしい症状になる。</li>
 * </ul>
 *
 * <h2>直し方</h2>
 * <p>メソッドを丸ごと置き換えると他Modと当たるので、枠 (usedOps) をlongで持ち直すだけにする:</p>
 * <ol>
 *   <li>{@link #insaneae$tickBudget} が {@code getCoProcessors()} の戻り値を
 *       「longで計算した予算 - 1」にすり替える。</li>
 *   <li>{@link #insaneae$rollUsedOps} が毎tick末にAE2の {@code usedOps} を0に戻す。</li>
 * </ol>
 * <p>結果、AE2の式は {@code (予算 - 1) + 1 - 0 = 予算} に退化する。
 * 元の計算式・元の実行フローはそのままに、桁あふれだけが消える。</p>
 *
 * <p>予算の上限が {@code Integer.MAX_VALUE} なのは {@code executeCrafting} の
 * 引数・戻り値が{@code int}だから。ここが最終的な天井で、1 tickに約21億操作。</p>
 *
 * <p>他Modが同じメソッドを触っていても起動を止めないよう、
 * {@code insaneae.optional.mixins.json} (required=false) 側に置いてある。
 * 適用されなければ「巨大階層でクラフトが進まない」という元の不具合に戻るだけで、他は動く。</p>
 */
@Mixin(value = CraftingCpuLogic.class, remap = false)
public abstract class CraftingCpuBudgetMixin {

    @Shadow
    @Final
    private int[] usedOps;

    /** 直近3 tickに使った操作数。long版で、AE2の{@link #usedOps}を置き換えるもの。 */
    @Unique
    private long insaneae$usedOps0;
    @Unique
    private long insaneae$usedOps1;
    @Unique
    private long insaneae$usedOps2;

    /** このtickで予算計算が走ったか。= ジョブ実行の枝に入ったか。 */
    @Unique
    private boolean insaneae$budgeted;

    /**
     * 1 tickの予算をlongで計算し、「予算 - 1」を {@code getCoProcessors()} の戻り値として渡す。
     * 直後にAE2が {@code + 1 - (usedOpsの合計)} を足すが、{@code usedOps} は
     * {@link #insaneae$rollUsedOps} が0に戻してあるので、結果はちょうど予算になる。
     */
    @Redirect(method = "tickCraftingLogic",
            at = @At(value = "INVOKE",
                    target = "Lappeng/me/cluster/implementations/CraftingCPUCluster;getCoProcessors()I"))
    private int insaneae$tickBudget(CraftingCPUCluster cluster) {
        insaneae$budgeted = true;

        // CraftingCPUClusterはfinalクラスなので、Mixinでインタフェースを足したことを
        // コンパイラは知らない。Object経由で確かめる
        long coProcessors = (Object) cluster instanceof ICoProcessorCount counted ?
                counted.insaneae$coProcessorCount()
                : cluster.getCoProcessors();

        // ここがlongなので、スレッド数がいくつでも溢れない
        long budget = coProcessors + 1 - (insaneae$usedOps0 + insaneae$usedOps1 + insaneae$usedOps2);
        // 使い切っている、つまり0以下のときは -1を返し、AE2の +1で0にして実行させない
        long clamped = Math.max(0, Math.min(budget, Integer.MAX_VALUE));
        return (int) clamped - 1;
    }

    /**
     * long版の枠を1つずらし、AE2のint配列を0に戻す。
     *
     * <p>AE2はこのメソッドの最後に {@code usedOps[0] = started - remainingOperations}
     * つまり、このtickに実際に送ったパターン数を書いているので、それをlong側へ移して片付ける。</p>
     */
    @Inject(method = "tickCraftingLogic", at = @At("RETURN"))
    private void insaneae$rollUsedOps(IEnergyService energyService, CraftingService craftingService,
            CallbackInfo ci) {
        if (!insaneae$budgeted) {
            // ジョブが無い / 中断されたtick。AE2もusedOpsを触らないので枠もずらさない
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
