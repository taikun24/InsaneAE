package jp.main.taikun.insaneae.crafting;

import appeng.crafting.CraftBranchFailure;
import appeng.crafting.CraftingTreeProcess;
import appeng.crafting.inv.ChildCraftingSimulationState;
import appeng.crafting.inv.CraftingSimulationState;
import jp.main.taikun.insaneae.mixin.crafting.CraftingTreeProcessInvoker;

import java.util.ArrayList;
import java.util.List;

/**
 * クラフト計算で「同じパターンを何度も繰り返す」部分をまとめて処理する。
 *
 * <h2>AE2が1回ずつしか進めない場所</h2>
 * <p>{@code CraftingTreeNode#request} は、必要数をまとめて1回で計算するのが基本だが、
 * 次の場合は{@code request(inv, 1)} のループになる:</p>
 * <ul>
 *   <li>パターンの出力が自分の入力にも含まれる。触媒や種など</li>
 *   <li>コンテナアイテムを使う。バケツのように、消費後に戻ってくる物</li>
 *   <li>同じアイテムを作れるパターンが複数ある。この場合は上の条件に関係なく1回ずつ</li>
 * </ul>
 * <p>どれか1つでもツリーに混ざると、その枝の計算量が要求数に比例する。
 * AE2は計算に1 tickあたり{@code craftingCalculationTimePerTick}、既定では5msしか使わないので、
 * 100万個も頼むと実時間で数分〜数十分かかる。</p>
 *
 * <h2>やっていること</h2>
 * <p>Quantum CPUのまとめクラフトと同じ発想で、
 * 1回ぶんだけ本当にシミュレートして、その差分をN回ぶんに掛け算する。</p>
 * <ol>
 *   <li>子の状態 ({@link ChildCraftingSimulationState}) に1回ぶんをシミュレートする。
 *       この時点では親の本物の状態は一切変わらない。</li>
 *   <li>その差分を2回ずつ重ねて1, 2, 4, 8 … 回ぶんの差分を作る。</li>
 *   <li>必要な回数を2進数で分解して積み上げ、最後に1回だけ親へ反映する。</li>
 * </ol>
 * <p>差分の重ね合わせはAE2の {@code CraftingSimulationState#applyDiff} をそのまま使うので、
 * 材料の消費・バイト数・{@code requiredExtract} の数え方はAE2本来のものと同じになる。</p>
 *
 * <h2>正しさについて</h2>
 * <p>「1回ぶんの差分をN倍してよい」のは、各回の材料が同じところから取れる限り。
 * 途中で足りなくなる場合は差分を重ねる時点で失敗するので、
 * そこまでの回数だけ反映して残りはAE2本来の1回ずつに戻す。
 * バケツのように「消費して戻ってくる」物は差分が ±0になるため、
 * N回ぶんまとめてもバケツ1個しか要求しない。AE2の逐次処理と同じ結論になる。</p>
 */
public final class CraftingCalculationBatch {

    /**
     * まとめ処理で消化したクラフト回数の累計。
     * ゲームテストが「本当にまとめ処理が働いたか」を確かめるためだけのもの。
     */
    public static volatile long batchedCrafts;

    private CraftingCalculationBatch() {
    }

    /**
     * {@code process} を最大 {@code times} 回ぶんまとめて {@code target} に反映する。
     *
     * @return実際に反映できた回数。{@code times} 未満なら残りは呼び出し元で処理すること。
     * @throws CraftBranchFailure 1回ぶんすら組めない場合。AE2本来の挙動と同じ
     */
    public static long apply(CraftingTreeProcess process, CraftingSimulationState target, long times)
            throws CraftBranchFailure, InterruptedException {
        CraftingTreeProcessInvoker invoker = (CraftingTreeProcessInvoker) process;

        // 1回ぶん。ここで失敗したらAE2と同じく呼び出し元へ投げる。枝の切り替えに使われる
        ChildCraftingSimulationState single = new ChildCraftingSimulationState(target);
        invoker.insaneae$request(single, 1);

        if (times <= 1) {
            single.applyDiff(target);
            return 1;
        }

        // 1, 2, 4, 8 … 回ぶんの差分を作る。作れなくなったらそこで打ち切り
        // timesを超える大きさの差分は作らない。2進数の積み上げにはtimes以下の段だけで
        // 足りるし、times超の段は「呼び出し側が保証した安全域、times × 材料数 ≤ long」の
        // 外に出るので、重ね合わせの途中で桁あふれしうる
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

        // 必要な回数を大きい単位から積む
        ChildCraftingSimulationState total = new ChildCraftingSimulationState(target);
        long done = 0;
        for (int step = steps.size() - 1; step >= 0 && done < times; step--) {
            long size = 1L << step;
            // done + sizeはlong境界で折り返すため、差分比較で上限を判定する
            while (size <= times - done && tryApply(steps.get(step), total)) {
                done += size;
            }
        }

        if (done == 0) {
            // 1回ぶんは組めているので、最低でもそれは反映する
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
     * @return反映できたらtrue
     */
    private static boolean tryApply(CraftingSimulationState diff, CraftingSimulationState into) {
        ChildCraftingSimulationState scratch = new ChildCraftingSimulationState(into);
        try {
            diff.applyDiff(scratch);
        } catch (IllegalStateException outOfMaterials) {
            // applyDiffは材料が足りないとIllegalStateExceptionを投げる
            // scratchは捨てるだけなのでintoは無傷
            return false;
        }
        scratch.applyDiff(into);
        return true;
    }
}
