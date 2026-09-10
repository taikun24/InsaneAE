package jp.main.taikun.insaneae.quantum.cpu;

/**
 * {@code CraftingCPUCluster} に生やした「持ち主」の出し入れ口
 * ({@code CraftingCpuClusterOwnerMixin} が実装を注入する)。
 *
 * <p>AE2 のクラスタは {@code final} なので継承できない。持ち主を覚えさせるために
 * <b>Mixin でフィールドを 1 本足し</b>、その読み書きをこの窓口に出している。</p>
 */
public interface QuantumOwnedCluster {

    void insaneae$setOwner(QuantumCpuClusterOwner owner);

    QuantumCpuClusterOwner insaneae$getOwner();
}
