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
}
