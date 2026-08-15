package jp.main.taikun.insaneae.quantum;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;

/**
 * 「同じパターンを N 回まとめて処理できる」クラフトプロバイダ。
 *
 * <p>AE2 の {@link ICraftingProvider#pushPattern} は<b>1 クラフト = 1 呼び出し</b>で、
 * 呼ぶ側 ({@code CraftingCpuLogic.executeCrafting}) が 1 回ごとに
 * {@code KeyCounter} の生成・材料の取り出し・電力の消費を行う。
 * つまりクラフト数に比例した固定コストが AE2 側に発生し、
 * 100 万クラフト/tick のような規模ではそこが支配的になる。</p>
 *
 * <p>そこで {@code CraftingCpuLogicMixin} が、このインタフェースを実装したプロバイダに対してだけ
 * 「材料を N 回ぶんまとめて取り出して 1 回で渡す」経路を用意する。
 * 実装していない普通のプロバイダには一切影響しない。</p>
 */
public interface IBulkCraftingProvider extends ICraftingProvider {

    /**
     * このパターンを今この tick にあと何回まとめて処理できるか。
     * 0 なら通常の 1 回ずつの経路にフォールバックする。
     */
    long getBulkCapacity(IPatternDetails details);

    /**
     * まとめて処理する。
     *
     * @param inputHolder {@code times} 回ぶんの材料。1 回ぶんを取り出した残りは
     *                    消費済みとして捨ててよい (呼び出し側は返さない)。
     * @param times       処理する回数。{@link #getBulkCapacity} 以下であることは呼び出し側が保証する。
     * @return 実際に処理した回数。0 なら何も起きなかったものとして扱われる。
     */
    long pushPatternBulk(IPatternDetails details, KeyCounter[] inputHolder, long times);

    /**
     * まとめ 1 回 (1 パターン × N クラフト) を、クラフト CPU の 1 tick 予算の
     * <b>1 操作</b>として数えてよいか (タスク統合カード)。
     *
     * <p>false (既定) ならクラフト回数ぶんを予算から引く。true のときの回数上限は
     * {@link #getBulkCapacity} だけになるので、実装側が自分の予算で頭打ちにすること。</p>
     */
    default boolean fusesOperations() {
        return false;
    }

    /**
     * 1 tick のうちに<b>同じパターンの窓を何度でも回してよいか</b> (加速カード満載時)。
     *
     * <p>1 窓に組める回数は long の会計に縛られる — 完成品は
     * {@code Long.MAX_VALUE / 出力数} まで、材料の取り出しも同様。
     * BigInteger 級の注文はこの窓を何枚も重ねないと終わらないが、
     * 窓の間で {@link #settleCompletedOutputs()} を呼んで完成待ちを清算すれば、
     * <b>1 tick の合計は long を超えられる</b> (どの瞬間の値も long に収まったまま)。</p>
     *
     * <p>false (既定) なら従来どおり 1 パターン 1 tick に 1 窓。</p>
     */
    default boolean repeatsWindowsWithinTick() {
        return false;
    }

    /**
     * 組み上がった完成品をネットワークへ流し、クラフト CPU の完成待ちを清算する。
     *
     * <p>{@link #repeatsWindowsWithinTick()} が true のとき、窓と窓の間で呼ばれる。
     * これをしないと完成待ちが 1 tick ぶん積み上がって long を溢れさせる。</p>
     */
    default void settleCompletedOutputs() {
    }
}
