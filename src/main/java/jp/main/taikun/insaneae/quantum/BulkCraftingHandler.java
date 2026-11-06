package jp.main.taikun.insaneae.quantum;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;

/**
 * 同じパターンを N 回まとめて受け取る処理の本体 (容量と実行)。
 *
 * <p>プロバイダ自身が実装するときは {@link IBulkCraftingProvider} を、InsaneAE の型を
 * 常にロードされるクラスに持ち込めない他 Mod は {@link BulkCraftingProviderAdapters} で
 * これを返すアダプタを登録する。忙しいかどうか ({@code isBusy}) とプロバイダの同一性は
 * 元の {@code ICraftingProvider} 側で判定するので、ここには含めない。</p>
 *
 * <p>呼ばれるのはサーバースレッドの、クラフト CPU の tick の中だけ。</p>
 */
public interface BulkCraftingHandler {

    /**
     * このパターンを今この tick にあと何回まとめて処理できるか。0 なら通常の 1 回ずつの経路に戻る。
     *
     * <p><b>1 回の受け渡しの間に何度も呼ばれる</b> (プロバイダを選ぶとき、回数の上限を決めるとき、
     * 同じ tick で次の窓を回すとき)。副作用を持たせず、軽くしておくこと。重い計算が要るなら
     * 実装側で tick 単位に覚えておいてよい。値は {@link #pushPatternBulk} の後でだけ変わる前提で使っている。</p>
     */
    long getBulkCapacity(IPatternDetails details);

    /**
     * まとめて処理する。
     *
     * <p>戻り値ごとの材料 ({@code inputHolder}) の扱い:</p>
     * <ul>
     *   <li>{@code times} — 全部処理した。{@code inputHolder} に残っているものは<b>消費済みとして捨てられる</b>
     *       (1 回組んで結果を掛け算する実装なら、組めた時点で中身に触れなくてよい)。</li>
     *   <li>{@code 0} — 何も起きなかった。呼び出し側が {@code inputHolder} の中身を<b>そのまま</b> CPU へ戻すので、
     *       0 を返すときは中身を取り出してはいけない。</li>
     *   <li>その間 ({@code done}) — {@code done} 回ぶんだけ処理した。残りの回数ぶんの材料を
     *       {@code inputHolder} に残しておけば、呼び出し側が CPU へ戻し、電力も残りの割合ぶん返す。</li>
     * </ul>
     * <p>全部か無しかの受け口は、{@code times} か {@code 0} だけを返していれば安全。</p>
     *
     * @param inputHolder {@code times} 回ぶんの材料 (パターンの入力ごと)
     * @param times       処理する回数。{@link #getBulkCapacity} 以下であることは呼び出し側が保証する
     * @return 実際に処理した回数 ({@code times} 以下)
     */
    long pushPatternBulk(IPatternDetails details, KeyCounter[] inputHolder, long times);

    /**
     * まとめ 1 回 (1 パターン × N クラフト) を、クラフト CPU の 1 tick 予算の
     * <b>1 操作</b>として数えてよいか (タスク統合カード)。
     *
     * <p>false (既定) ならクラフト回数ぶんを予算から引く。true のときの回数上限は
     * {@link #getBulkCapacity} だけになる (CPU の予算では頭打ちにしない) ので、
     * 実装側が自分の予算 (1 tick あたりの処理数、材料を置く場所の空きなど) で頭打ちにした値を返すこと。
     * どちらの場合も、完成品の数が long に収まる回数までには呼び出し側が絞る。</p>
     */
    default boolean fusesOperations() {
        return false;
    }

    /**
     * 1 tick のうちに<b>同じパターンの窓を何度でも回してよいか</b> (加速カード満載時)。
     *
     * <p>1 窓に組める回数は long の会計に縛られる。完成品は
     * {@code Long.MAX_VALUE / 出力数} まで、材料の取り出しも同様。
     * BigInteger 級の注文はこの窓を何枚も重ねないと終わらないが、
     * 窓の間で {@link #settleCompletedOutputs()} を呼んで完成待ちを清算すれば、
     * <b>1 tick の合計は long を超えられる</b> (どの瞬間の値も long に収まったまま)。</p>
     *
     * <p>false (既定) なら 1 パターンにつき 1 tick に 1 窓。</p>
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
