package jp.main.taikun.insaneae.mixin;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;
import appeng.crafting.inv.CraftingSimulationState;
import jp.main.taikun.insaneae.config.InsaneAEConfig;
import jp.main.taikun.insaneae.crafting.CraftingCalculationBatch;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * クラフト計算で「同じパターンを 1 回ずつ」繰り返している部分をまとめて処理させる。
 *
 * <p>{@code CraftingTreeNode#request} には {@code CraftingTreeProcess#request(state, times)} の
 * 呼び出しが 2 箇所ある:</p>
 * <ul>
 *   <li>パターンが 1 つの枝: 普通は必要数をまとめて 1 回で計算するが、
 *       {@code limitsQuantity()} が true のパターンだけ {@code times = 1} のループになる。</li>
 *   <li>パターンが複数ある枝: {@code limitsQuantity()} に関係なく<b>常に</b> {@code times = 1} のループ。</li>
 * </ul>
 *
 * <p>どちらも {@code times == 1} で呼ばれるので、そこを掴まえて
 * {@link CraftingCalculationBatch} に回す。まとめられない状況では AE2 本来の呼び出しに素通しする。</p>
 *
 * <p>設定 {@code craftingCalculation.batchCraftingCalculation} で切れる。
 * 注入はすべて {@code require = 0} なので、AE2 側の作りが変わっても<b>計算が遅いだけに戻る</b>。</p>
 */
@Mixin(value = CraftingTreeNode.class, remap = false)
public abstract class CraftingTreeNodeMixin {

    @Shadow
    @Final
    private AEKey what;

    /** ループが「あと何個必要としているか」。{@link #insaneae$trackRemaining} が追いかける。 */
    @Unique
    private long insaneae$remainingItems;

    /**
     * まとめ処理が最後まで届かなかった (＝途中で材料が尽きた) 印。
     * 以降その枝では AE2 本来の 1 回ずつに任せる。
     */
    @Unique
    private boolean insaneae$batchExhausted;

    @Inject(method = "request", at = @At("HEAD"), require = 0)
    private void insaneae$resetBatchState(CraftingSimulationState inv, long requestedAmount,
            @Nullable KeyCounter containerItems, CallbackInfo ci) {
        insaneae$remainingItems = 0;
        insaneae$batchExhausted = false;
    }

    /**
     * ループの残り数を覚えておく。値は変えない。
     *
     * <p>これが取れなかった場合 (AE2 のビルドに局所変数名が無い等) は
     * {@link #insaneae$remainingItems} が 0 のままになり、まとめ処理は働かない。</p>
     */
    @ModifyVariable(method = "request", at = @At("STORE"), name = "totalRequestedItems", require = 0)
    private long insaneae$trackRemaining(long totalRequestedItems) {
        insaneae$remainingItems = totalRequestedItems;
        return totalRequestedItems;
    }

    @Redirect(method = "request",
            at = @At(value = "INVOKE",
                    target = "Lappeng/crafting/CraftingTreeProcess;request("
                            + "Lappeng/crafting/inv/CraftingSimulationState;J)V"),
            require = 0)
    private void insaneae$batchRequest(CraftingTreeProcess process, CraftingSimulationState target, long times)
            throws CraftBranchFailure, InterruptedException {
        CraftingTreeProcessInvoker invoker = (CraftingTreeProcessInvoker) process;

        // ---- long あふれの門番 --------------------------------------------------
        // request() の中は「材料数 × times」「出力数 × times」を<b>ガード無しの生の long</b> で掛ける。
        // 素の AE2 はそこに届く前に計算時間で死ぬが、まとめ計算が 8^20 級を現実に計算可能に
        // したので、この掛け算が最初に溢れる (8^21 = 2^63 でちょうど long を超える)。
        // 溢れる要求は ME に置くことも計画に載せることもできないので、負の量を黙って流す
        // 代わりに CraftBranchFailure で枝を落とす。この例外は AE2 の runCraftAttempt が
        // 受け止める正規の失敗経路で、計算スレッドは壊れない。
        long safeTimes = Long.MAX_VALUE / insaneae$maxUnitAmount(invoker);
        if (times > safeTimes) {
            throw new CraftBranchFailure(what, times);
        }

        if (times != 1 || insaneae$batchExhausted || insaneae$remainingItems <= 0
                || !InsaneAEConfig.batchCraftingCalculation()) {
            invoker.insaneae$request(target, times);
            return;
        }

        long perCraft = invoker.insaneae$getOutputCount(what);
        if (perCraft <= 0) {
            invoker.insaneae$request(target, times);
            return;
        }

        // 足し算で切り上げると remainingItems が上限付近のとき溢れるので、割り算だけで組む。
        long needed = insaneae$remainingItems / perCraft
                + (insaneae$remainingItems % perCraft == 0 ? 0 : 1);
        if (needed < InsaneAEConfig.craftingBatchThreshold()) {
            invoker.insaneae$request(target, times);
            return;
        }
        if (needed > safeTimes) {
            // まとめた合計が long で表現できない。上と同じ門番 (こちらは times=1 ループ側)。
            throw new CraftBranchFailure(what, insaneae$remainingItems);
        }

        long done = CraftingCalculationBatch.apply(process, target, needed);
        if (done < needed) {
            // 材料が尽きた。残りは AE2 のループがそのまま面倒を見る。
            insaneae$batchExhausted = true;
        }
    }

    /**
     * この枝の 1 クラフトあたりの最大アイテム数 (材料・完成品の両方)。
     * {@code times} との積が long に収まるかの判定に使う。
     */
    @Unique
    private static long insaneae$maxUnitAmount(CraftingTreeProcessInvoker invoker) {
        long max = 1;
        for (Long amount : invoker.insaneae$getNodes().values()) {
            max = Math.max(max, amount);
        }
        for (GenericStack output : invoker.insaneae$getDetails().getOutputs()) {
            max = Math.max(max, output.amount());
        }
        return max;
    }
}
