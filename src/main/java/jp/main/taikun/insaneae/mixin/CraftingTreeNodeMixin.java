package jp.main.taikun.insaneae.mixin;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;
import appeng.crafting.inv.CraftingSimulationState;
import jp.main.taikun.insaneae.config.InsaneAEConfig;
import jp.main.taikun.insaneae.crafting.CraftingCalculationBatch;
import jp.main.taikun.insaneae.integration.aco.AcoCalculationIntegration;
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

        if (times != 1 || insaneae$batchExhausted || insaneae$remainingItems <= 0
                || AcoCalculationIntegration.shouldDeferCalculationBatch()
                || !InsaneAEConfig.batchCraftingCalculation()) {
            invoker.insaneae$request(target, times);
            return;
        }

        long perCraft = invoker.insaneae$getOutputCount(what);
        if (perCraft <= 0) {
            invoker.insaneae$request(target, times);
            return;
        }

        // 加算してから切り上げる式は、Long.MAX_VALUE付近でオーバーフローする。
        // 商と余りで切り上げると、必要数がlong範囲内である限り加算を使わずに済む。
        long needed = insaneae$remainingItems / perCraft;
        if (insaneae$remainingItems % perCraft != 0L) {
            // perCraftは正数なので、商がLong.MAX_VALUEになるケースは発生しない。
            needed++;
        }
        if (needed < InsaneAEConfig.craftingBatchThreshold()) {
            invoker.insaneae$request(target, times);
            return;
        }

        long done = CraftingCalculationBatch.apply(process, target, needed);
        if (done < needed) {
            // 材料が尽きた。残りは AE2 のループがそのまま面倒を見る。
            insaneae$batchExhausted = true;
        }
    }
}
