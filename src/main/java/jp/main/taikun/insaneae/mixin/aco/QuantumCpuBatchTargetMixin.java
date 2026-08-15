package jp.main.taikun.insaneae.mixin.aco;

import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchRequest;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchSnapshot;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchTarget;
import java.util.Optional;
import java.util.UUID;
import jp.main.taikun.insaneae.integration.aco.batch.QuantumCpuBatchExecutor;
import jp.main.taikun.insaneae.quantum.QuantumCpuBlockEntity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Quantum CPU を ACO の craftingtable batch ターゲットにする。
 *
 * <p>ACO はターゲットを「{@code ICraftingProvider} が {@code ProviderOwnedPatternBatchTarget} で、
 * 返した BlockEntity が {@code CraftingTableBatchTarget}」という形で探す
 * ({@code PhysicalCraftingTreeTransaction.selectTarget})。
 * <b>アダプタの登録は要らない。</b></p>
 *
 * <p>インターフェイスの実装は反射では代用できないので、ACO の型をここで直接参照している。
 * ACO が無い環境では {@link AcoMixinPlugin} がこの Mixin ごと適用を止める
 * (実装したはずのインターフェイスが存在せず、Quantum CPU がロードできなくなるため)。</p>
 *
 * <p>中身は {@link QuantumCpuBatchExecutor} に置いてある。Mixin のクラスは
 * デバッグしづらいので、判定と会計は普通のクラスに出しておく。</p>
 */
@Mixin(QuantumCpuBlockEntity.class)
public abstract class QuantumCpuBatchTargetMixin implements CraftingTableBatchTarget {

    @Override
    public boolean aco$acceptCraftingTableBatch(CraftingTableBatchRequest request) {
        return QuantumCpuBatchExecutor.accept(insaneae$self(), request);
    }

    @Override
    public boolean aco$ownsCraftingTableBatch(UUID transactionId, String payloadDigest) {
        return QuantumCpuBatchExecutor.owns(insaneae$self(), transactionId, payloadDigest);
    }

    @Override
    public Optional<CraftingTableBatchSnapshot> aco$craftingTableBatchSnapshot(UUID transactionId,
            String payloadDigest) {
        return QuantumCpuBatchExecutor.snapshot(insaneae$self(), transactionId, payloadDigest);
    }

    @Override
    public boolean aco$acknowledgeCraftingTableBatch(UUID transactionId, String payloadDigest) {
        return QuantumCpuBatchExecutor.acknowledge(insaneae$self(), transactionId, payloadDigest);
    }

    @Override
    public boolean aco$forgetCraftingTableBatch(UUID transactionId, String payloadDigest) {
        return QuantumCpuBatchExecutor.forget(insaneae$self(), transactionId, payloadDigest);
    }

    @Override
    public boolean aco$cancelCraftingTableBatch(UUID transactionId, String payloadDigest) {
        return QuantumCpuBatchExecutor.cancel(insaneae$self(), transactionId, payloadDigest);
    }

    private QuantumCpuBlockEntity insaneae$self() {
        return (QuantumCpuBlockEntity) (Object) this;
    }
}
