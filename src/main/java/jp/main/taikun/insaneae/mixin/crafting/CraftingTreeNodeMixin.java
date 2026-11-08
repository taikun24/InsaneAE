package jp.main.taikun.insaneae.mixin.crafting;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
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
import appeng.crafting.CraftingCalculation;

/**
 * クラフト計算で「同じパターンを1回ずつ」繰り返している部分をまとめて処理させる。
 *
 * <p>{@code CraftingTreeNode#request} には {@code CraftingTreeProcess#request(state, times)} の
 * 呼び出しが2箇所ある:</p>
 * <ul>
 *   <li>パターンが1つの枝: 普通は必要数をまとめて1回で計算するが、
 *       {@code limitsQuantity()} がtrueのパターンだけ {@code times = 1} のループになる。</li>
 *   <li>パターンが複数ある枝: {@code limitsQuantity()} に関係なく常に {@code times = 1} のループ。</li>
 * </ul>
 *
 * <p>どちらも {@code times == 1} で呼ばれるので、そこを掴まえて
 * {@link CraftingCalculationBatch} に回す。まとめられない状況ではAE2本来の呼び出しに素通しする。</p>
 *
 * <p>設定 {@code craftingCalculation.batchCraftingCalculation} で切れる。
 * 注入はすべて {@code require = 0} なので、AE2側の作りが変わっても計算が遅いだけに戻る。</p>
 */
@Mixin(value = CraftingTreeNode.class, remap = false)
public abstract class CraftingTreeNodeMixin {

    @Shadow
    @Final
    private AEKey what;

    @Shadow
    @Final
    private CraftingCalculation job;

    /** ループが「あと何個必要としているか」。{@link #insaneae$trackRemaining} が追いかける。 */
    @Unique
    private long insaneae$remainingItems;

    /**
     * まとめ処理が最後まで届かなかった印。途中で材料が尽きたことを表す。
     * 以降その枝ではAE2本来の1回ずつに任せる。
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
     * <p>これが取れなかった場合、たとえばAE2のビルドに局所変数名が無いときは
     * {@link #insaneae$remainingItems} が0のままになり、まとめ処理は働かない。</p>
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

        // ---- longあふれの門番 --------------------------------------------------
        // request() の中は「材料数 × times」「出力数 × times」をガード無しの生のlongで掛ける
        // 素のAE2はそこに届く前に計算時間で死ぬが、まとめ計算が8^20級を現実に計算可能に
        // したので、この掛け算が最初に溢れる。8^21 = 2^63でちょうどlongを超える
        // 溢れる要求はMEに置くことも計画に載せることもできないので、負の量を黙って流さない
        long safeTimes = Long.MAX_VALUE / insaneae$maxUnitAmount(invoker);
        if (times > safeTimes) {
            times = insaneae$clampOrFail(times, safeTimes);
        }

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

        // 足し算で切り上げるとremainingItemsが上限付近のとき溢れるので、割り算だけで組む
        long needed = insaneae$remainingItems / perCraft
                + (insaneae$remainingItems % perCraft == 0 ? 0 : 1);
        if (needed < InsaneAEConfig.craftingBatchThreshold()) {
            invoker.insaneae$request(target, times);
            return;
        }
        if (needed > safeTimes) {
            // まとめた合計がlongで表現できない。times=1ループ側の門番の対象
            // 実行パスでは枝を落とす。シミュレーションではAE2素の1回ずつに素通しする —
            // シミュレーション中のAE2は「プロセスは必ず完成品を出す」前提で、
            // 進捗ゼロはサニティチェック (can't find created items) に当たり、
            // 丸めて進めても周回の集計が溢れて途中から1回ずつに落ち、どのみち終わらない
            // どちらもゲームテストで確認した。素通しは素のAE2と同じ「終わらない計算」に
            // なるが、この経路を踏むには10^18個超の直接発注が必要で、
            // 素の発注UIでは入力できない量なので実害は無い
            if (job.isSimulation()) {
                insaneae$batchExhausted = true;
                invoker.insaneae$request(target, times);
                return;
            }
            throw new CraftBranchFailure(what, insaneae$remainingItems);
        }

        long done = CraftingCalculationBatch.apply(process, target, needed);
        if (done < needed) {
            // 材料が尽きた。残りはAE2のループがそのまま面倒を見る
            insaneae$batchExhausted = true;
        }
    }

    /**
     * 溢れる要求をAE2の流儀で落とす。単一パターンの一括経路用。
     *
     * <p>実行パスでは {@code CraftBranchFailure} を投げる — {@code runCraftAttempt} が
     * 受け止める正規の失敗経路。実行の試行が失敗すると、AE2は必ずシミュレーションの
     * 試行をやり直すので、ユーザーに返る計画は常に{@code simulation=true}、つまり提出不可になる。</p>
     *
     * <p>シミュレーションパスでは投げても止まってもいけない。投げると例外がどのcatchにも
     * 掛からず計画がnullになり、提出側の{@code result.simulation()}がNPEを吐く。実機で確認した。
     * 何もせず戻ると、この一括経路は「作ったはずの完成品が無い」の
     * {@code UnsupportedOperationException}を投げる。ゲームテストで確認した。
     * そこで安全な回数に丸めて普通に走らせる。呼び出しは1回きりなので溢れない。
     * 足りないぶんはAE2自身が欠品として計上し、いつもの赤い「作成不可」画面で終わる。
     * 丸めた結果、材料が潤沢で全部作れたように見えても、シミュレーション計画は
     * 提出できないので実害は無い。</p>
     */
    @Unique
    private long insaneae$clampOrFail(long requested, long safeTimes) throws CraftBranchFailure {
        if (!job.isSimulation()) {
            throw new CraftBranchFailure(what, requested);
        }
        return safeTimes;
    }

    /**
     * この枝の1クラフトあたりの最大アイテム数。材料と完成品の両方。
     * {@code times} との積がlongに収まるかの判定に使う。
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
