package jp.main.taikun.insaneae.quantum.cpu;

import appeng.api.networking.IGridNode;
import net.minecraft.world.level.Level;

/**
 * AE2 の {@code CraftingCPUCluster} に「持ち主」として渡せるもの。
 *
 * <p>AE2 のクラスタは自分の情報 (ノード・ワールド・保存要求) を
 * <b>構成ブロック ({@code CraftingBlockEntity}) から引く</b>作りになっている。
 * Quantum CPU の内蔵 CPU には構成ブロックが 1 つも無いので、
 * 代わりにこれを差しておき、{@code CraftingCpuClusterOwnerMixin} が
 * その 3 つの経路だけを横取りする。</p>
 *
 * <p>実装は {@link QuantumCraftingCpu} だけ。AE2 が自分で組んだクラスタでは
 * 持ち主が null なので、<b>AE2 本来の挙動は 1 バイトも変わらない</b>。</p>
 */
public interface QuantumCpuClusterOwner {

    /** クラスタのノード。AE2 では「代表ブロックのノード」にあたるもの。 */
    IGridNode cpuNode();

    /** クラスタのあるワールド。 */
    Level cpuLevel();

    /** クラスタの中身が変わったので保存が要る。 */
    void cpuMarkDirty();

    /**
     * 合計バイト数の正本。
     *
     * <p>InsaneAE の上位階層は 1 個で 2^63 バイトあり long に収まらないので、
     * AE2 の {@code storage} フィールド (long) ではなくこちらが正本になる。</p>
     */
    java.math.BigInteger cpuExactStorage();

    /** 合計スレッド数。AE2 の {@code accelerator} は int なので、正本はこちら。 */
    long cpuCoProcessors();

    /**
     * 中身の版番号。変わるたびに増える。
     *
     * <p>AE2 のクラスタは「構成ブロック数」を世代の目印にして数え直しを省いているが、
     * 内蔵 CPU には構成ブロックが無いのでこれを代わりに使う。</p>
     */
    int cpuRevision();
}
