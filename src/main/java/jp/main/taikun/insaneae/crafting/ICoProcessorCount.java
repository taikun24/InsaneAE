package jp.main.taikun.insaneae.crafting;

/**
 * クラフト CPU が持つ協調処理スレッドの合計を <b>long で</b> 取り出すための窓口。
 *
 * <p>AE2 の {@code CraftingCPUCluster#getCoProcessors()} は {@code int} 返しなので、
 * 21 億を超える合計をそのまま扱えない。{@code CraftingCPUClusterMixin} がこのインタフェースを
 * クラスタに生やし、クラスタを構成するブロックから long で数え直す。使うのは
 * {@code CraftingCpuBudgetMixin} で、1 tick の予算計算を long で行うため。</p>
 *
 * <p>AE2 側のクラスに Mixin でインタフェースを足しているだけなので、
 * <b>キャストして使う前に {@code instanceof} で確かめること</b> (Mixin が適用されない環境向け)。</p>
 */
public interface ICoProcessorCount {

    /** 協調処理スレッドの合計 (long)。 */
    long insaneae$coProcessorCount();
}
