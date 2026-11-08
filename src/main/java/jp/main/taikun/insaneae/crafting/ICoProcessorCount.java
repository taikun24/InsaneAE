package jp.main.taikun.insaneae.crafting;

/**
 * クラフトCPUが持つ協調処理スレッドの合計をlongで取り出すための窓口。
 *
 * <p>AE2の {@code CraftingCPUCluster#getCoProcessors()} は {@code int} 返しなので、
 * 21億を超える合計をそのまま扱えない。{@code CraftingCPUClusterMixin} がこのインタフェースを
 * クラスタに生やし、クラスタを構成するブロックからlongで数え直す。使うのは
 * {@code CraftingCpuBudgetMixin} で、1 tickの予算計算をlongで行うため。</p>
 *
 * <p>AE2側のクラスにMixinでインタフェースを足しているだけなので、
 * キャストして使う前に{@code instanceof}で確かめること。Mixinが適用されない環境向けに必要。</p>
 */
public interface ICoProcessorCount {

    /** 協調処理スレッドの合計 (long)。 */
    long insaneae$coProcessorCount();
}
