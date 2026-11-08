package jp.main.taikun.insaneae.quantum;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;

/**
 * 同じパターンをN回まとめて受け取る処理の本体。容量と実行を扱う。
 *
 * <p>プロバイダ自身が実装するときは {@link IBulkCraftingProvider} を、InsaneAEの型を
 * 常にロードされるクラスに持ち込めない他Modは {@link BulkCraftingProviderAdapters} で
 * これを返すアダプタを登録する。忙しいかどうか ({@code isBusy}) とプロバイダの同一性は
 * 元の {@code ICraftingProvider} 側で判定するので、ここには含めない。</p>
 *
 * <p>呼ばれるのはサーバースレッドの、クラフトCPUのtickの中だけ。</p>
 */
public interface BulkCraftingHandler {

    /**
     * このパターンを今このtickにあと何回まとめて処理できるか。0なら通常の1回ずつの経路に戻る。
     *
     * <p>1回の受け渡しの間に何度も呼ばれる。プロバイダを選ぶとき、回数の上限を決めるとき、
     * 同じtickで次の窓を回すとき)。副作用を持たせず、軽くしておくこと。重い計算が要るなら
     * 実装側でtick単位に覚えておいてよい。値は {@link #pushPatternBulk} の後でだけ変わる前提で使っている。</p>
     */
    long getBulkCapacity(IPatternDetails details);

    /**
     * まとめて処理する。
     *
     * <p>戻り値ごとの材料 ({@code inputHolder}) の扱い:</p>
     * <ul>
     *   <li>{@code times} — 全部処理した。{@code inputHolder} に残っているものは消費済みとして捨てられる
     *       1回組んで結果を掛け算する実装なら、組めた時点で中身に触れなくてよい。</li>
     *   <li>{@code 0} — 何も起きなかった。呼び出し側が {@code inputHolder} の中身をそのままCPUへ戻すので、
     *       0を返すときは中身を取り出してはいけない。</li>
     *   <li>その間 ({@code done}) — {@code done} 回ぶんだけ処理した。残りの回数ぶんの材料を
     *       {@code inputHolder} に残しておけば、呼び出し側がCPUへ戻し、電力も残りの割合ぶん返す。</li>
     * </ul>
     * <p>全部か無しかの受け口は、{@code times} か {@code 0} だけを返していれば安全。</p>
     *
     * @param inputHolder {@code times}回ぶんの材料。パターンの入力ごと
     * @param times処理する回数。{@link #getBulkCapacity} 以下であることは呼び出し側が保証する
     * @return実際に処理した回数。{@code times}以下
     */
    long pushPatternBulk(IPatternDetails details, KeyCounter[] inputHolder, long times);

    /**
     * まとめ1回、つまり1パターン × Nクラフトを、クラフトCPUの1 tick予算の
     * 1操作として数えてよいか。タスク統合カードの場合。
     *
     * <p>falseが既定で、クラフト回数ぶんを予算から引く。trueのときの回数上限は
     * {@link #getBulkCapacity}だけになる。CPUの予算では頭打ちにしないので、
     * 実装側が、自分の予算、たとえば1 tickあたりの処理数や材料を置く場所の空きで頭打ちにした値を返すこと。
     * どちらの場合も、完成品の数がlongに収まる回数までには呼び出し側が絞る。</p>
     */
    default boolean fusesOperations() {
        return false;
    }

    /**
     * 1 tickのうちに同じパターンの窓を何度でも回してよいか。加速カード満載時の話。
     *
     * <p>1窓に組める回数はlongの会計に縛られる。完成品は
     * {@code Long.MAX_VALUE / 出力数} まで、材料の取り出しも同様。
     * BigInteger級の注文はこの窓を何枚も重ねないと終わらないが、
     * 窓の間で {@link #settleCompletedOutputs()} を呼んで完成待ちを清算すれば、
     * 1 tickの合計はlongを超えられる。どの瞬間の値もlongに収まったまま。</p>
     *
     * <p>falseが既定で、1パターンにつき1 tickに1窓。</p>
     */
    default boolean repeatsWindowsWithinTick() {
        return false;
    }

    /**
     * 組み上がった完成品をネットワークへ流し、クラフトCPUの完成待ちを清算する。
     *
     * <p>{@link #repeatsWindowsWithinTick()} がtrueのとき、窓と窓の間で呼ばれる。
     * これをしないと完成待ちが1 tickぶん積み上がってlongを溢れさせる。</p>
     */
    default void settleCompletedOutputs() {
    }
}
