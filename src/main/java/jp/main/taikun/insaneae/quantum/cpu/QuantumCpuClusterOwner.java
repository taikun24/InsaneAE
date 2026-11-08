package jp.main.taikun.insaneae.quantum.cpu;

import appeng.api.networking.IGridNode;
import net.minecraft.world.level.Level;

/**
 * AE2の {@code CraftingCPUCluster} に「持ち主」として渡せるもの。
 *
 * <p>AE2のクラスタは自分の情報、つまりノード、ワールド、保存要求を
 * 構成ブロック ({@code CraftingBlockEntity}) から引く作りになっている。
 * Quantum CPUの内蔵CPUには構成ブロックが1つも無いので、
 * 代わりにこれを差しておき、{@code CraftingCpuClusterOwnerMixin} が
 * その3つの経路だけを横取りする。</p>
 *
 * <p>実装は {@link QuantumCraftingCpu} だけ。AE2が自分で組んだクラスタでは
 * 持ち主がnullなので、AE2本来の挙動は1バイトも変わらない。</p>
 */
public interface QuantumCpuClusterOwner {

    /** クラスタのノード。AE2では「代表ブロックのノード」にあたるもの。 */
    IGridNode cpuNode();

    /** クラスタのあるワールド。 */
    Level cpuLevel();

    /** クラスタの中身が変わったので保存が要る。 */
    void cpuMarkDirty();

    /**
     * 合計バイト数の正確な値。
     *
     * <p>InsaneAEの上位階層は1個で2^63バイトありlongに収まらないので、
     * AE2の {@code storage} フィールド (long) ではなくこちらを基準にする。</p>
     */
    java.math.BigInteger cpuExactStorage();

    /** 合計スレッド数。AE2の {@code accelerator} はintなので、基準にするのはこちら。 */
    long cpuCoProcessors();

    /**
     * このCPUがジョブを受け取った。
     *
     * <p>Quantum CPUは容量を切り分けて何本も同時に受けるので、
     * 受け取った時点で自分の容量をそのジョブのぶんへ縮める必要がある
     * {@link QuantumCpuPartition}から{@link QuantumCraftingCpu}へ。
     * 呼び元は{@code CraftingCpuClusterOwnerMixin}で、{@code submitJob}の成功時。</p>
     *
     * @param bytesそのジョブが要求したバイト数。ACOのBigInteger計画なら正確値
     */
    void cpuJobSubmitted(java.math.BigInteger bytes);

    /**
     * 中身の版番号。変わるたびに増える。
     *
     * <p>AE2のクラスタは「構成ブロック数」を世代の目印にして数え直しを省いているが、
     * 内蔵CPUには構成ブロックが無いのでこれを代わりに使う。</p>
     */
    int cpuRevision();
}
