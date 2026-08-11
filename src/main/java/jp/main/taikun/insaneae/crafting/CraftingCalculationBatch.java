package jp.main.taikun.insaneae.crafting;

import appeng.crafting.CraftBranchFailure;
import appeng.crafting.CraftingTreeProcess;
import appeng.crafting.inv.ChildCraftingSimulationState;
import appeng.crafting.inv.CraftingSimulationState;
import jp.main.taikun.insaneae.mixin.CraftingTreeProcessInvoker;

import java.util.ArrayList;
import java.util.List;

/**
 * クラフト計算で「同じパターンを何度も繰り返す」部分をまとめて処理する。
 *
 * <h2>AE2 が 1 回ずつしか進めない場所</h2>
 * <p>{@code CraftingTreeNode#request} は、必要数をまとめて 1 回で計算するのが基本だが、
 * 次の場合は <b>{@code request(inv, 1)} のループ</b>になる:</p>
 * <ul>
 *   <li>パターンの<b>出力が自分の入力にも含まれる</b> (触媒・種など)</li>
 *   <li><b>コンテナアイテム</b>を使う (バケツのように消費後に戻ってくる物)</li>
 *   <li>同じアイテムを作れる<b>パターンが複数ある</b> (この場合は上の条件に関係なく 1 回ずつ)</li>
 * </ul>
 * <p>どれか 1 つでもツリーに混ざると、その枝の計算量が<b>要求数に比例</b>する。
 * AE2 は計算に 1 tick あたり {@code craftingCalculationTimePerTick} (既定 5ms) しか使わないので、
 * 100 万個も頼むと実時間で数分〜数十分かかる。</p>
 *
 * <h2>やっていること</h2>
 * <p>Quantum CPU のまとめクラフトと同じ発想で、
 * <b>1 回ぶんだけ本当にシミュレートして、その差分を N 回ぶんに掛け算する</b>。</p>
 * <ol>
 *   <li>子の状態 ({@link ChildCraftingSimulationState}) に 1 回ぶんをシミュレートする。
 *       この時点では親 (本物の状態) は一切変わらない。</li>
 *   <li>その差分を 2 回ずつ重ねて 1, 2, 4, 8 … 回ぶんの差分を作る。</li>
 *   <li>必要な回数を 2 進数で分解して積み上げ、最後に 1 回だけ親へ反映する。</li>
 * </ol>
 * <p>差分の重ね合わせは AE2 の {@code CraftingSimulationState#applyDiff} をそのまま使うので、
 * 材料の消費・バイト数・{@code requiredExtract} の数え方は AE2 本来のものと同じになる。</p>
 *
 * <h2>正しさについて</h2>
 * <p>「1 回ぶんの差分を N 倍してよい」のは、<b>各回の材料が同じところから取れる限り</b>。
 * 途中で足りなくなる場合は差分を重ねる時点で失敗するので、
 * <b>そこまでの回数だけ反映して残りは AE2 本来の 1 回ずつに戻す</b>。
 * バケツのように「消費して戻ってくる」物は差分が ±0 になるため、
 * N 回ぶんまとめても<b>バケツ 1 個しか要求しない</b> (＝ AE2 の逐次処理と同じ結論になる)。</p>
 */
public final class CraftingCalculationBatch {

    /**
     * まとめ処理で消化したクラフト回数の累計。
     * <b>ゲームテストが「本当にまとめ処理が働いたか」を確かめるためだけのもの。</b>
     */
    public static volatile long batchedCrafts;

    private CraftingCalculationBatch() {
    }

    /**
     * {@code process} を最大 {@code times} 回ぶんまとめて {@code target} に反映する。
     *
     * @return 実際に反映できた回数。{@code times} 未満なら残りは呼び出し元で処理すること。
     * @throws CraftBranchFailure 1 回ぶんすら組めない場合 (AE2 本来の挙動と同じ)
     */
    public static long apply(CraftingTreeProcess process, CraftingSimulationState target, long times)
            throws CraftBranchFailure, InterruptedException {
        CraftingTreeProcessInvoker invoker = (CraftingTreeProcessInvoker) process;

        // 1 回ぶん。ここで失敗したら AE2 と同じく呼び出し元へ投げる (枝の切り替えに使われる)。
        ChildCraftingSimulationState single = new ChildCraftingSimulationState(target);
        invoker.insaneae$request(single, 1);

        if (times <= 1) {
            single.applyDiff(target);
            return 1;
        }

        // 1, 2, 4, 8 … 回ぶんの差分を作る。作れなくなったらそこで打ち切り。
        // <b>times を超える大きさの差分は作らない</b>。2 進数の積み上げには times 以下の段だけで
        // 足りるし、times 超の段は「呼び出し側が保証した安全域 (times × 材料数 ≤ long)」の
        // 外に出るので、重ね合わせの途中で桁あふれしうる。
        List<CraftingSimulationState> steps = new ArrayList<>();
        steps.add(single);
        while (steps.size() < Long.SIZE - 1 && (1L << steps.size()) <= times) {
            CraftingSimulationState previous = steps.get(steps.size() - 1);
            ChildCraftingSimulationState doubled = new ChildCraftingSimulationState(target);
            if (!tryApply(previous, doubled) || !tryApply(previous, doubled)) {
                break;
            }
            steps.add(doubled);
        }

        // 必要な回数を大きい単位から積む。
        ChildCraftingSimulationState total = new ChildCraftingSimulationState(target);
        long done = 0;
        for (int step = steps.size() - 1; step >= 0 && done < times; step--) {
            long size = 1L << step;
            while (done + size <= times && tryApply(steps.get(step), total)) {
                done += size;
            }
        }

        if (done == 0) {
            // 1 回ぶんは組めているので、最低でもそれは反映する。
            single.applyDiff(target);
            return 1;
        }

        total.applyDiff(target);
        batchedCrafts += done;
        return done;
    }

    /**
     * 差分を重ねる。足りずに失敗しても {@code into} を壊さないよう、
     * 使い捨ての子に一度乗せてから移す。
     *
     * @return 反映できたら true
     */
    private static boolean tryApply(CraftingSimulationState diff, CraftingSimulationState into) {
        ChildCraftingSimulationState scratch = new ChildCraftingSimulationState(into);
        try {
            diff.applyDiff(scratch);
        } catch (IllegalStateException outOfMaterials) {
            // applyDiff は材料が足りないと IllegalStateException を投げる。
            // scratch は捨てるだけなので into は無傷。
            return false;
        }
        scratch.applyDiff(into);
        return true;
    }
}
