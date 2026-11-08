package jp.main.taikun.insaneae.mixin.aco;

import com.syaru.ae2craftingoptimizer.api.batch.v2.ProviderOwnedPatternBatchTarget;
import jp.main.taikun.insaneae.quantum.QuantumCpuLogic;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * ACOに「このクラフトプロバイダの実体はこのBlockEntity」と教える。
 *
 * <p>ACOは {@code CraftingService.getProviders(pattern)} で候補を集め、
 * {@code ProviderOwnedPatternBatchTarget} を実装しているものからBlockEntityを引き、
 * それが {@code CraftingTableBatchTarget} ならbatchのターゲットにする。
 * つまりこの1メソッドが無いとQuantum CPUは候補にすら入らない。</p>
 */
@Mixin(QuantumCpuLogic.class)
public abstract class QuantumCpuProviderTargetMixin implements ProviderOwnedPatternBatchTarget {

    @Override
    public BlockEntity aco$getProviderOwnedBatchTarget() {
        return ((QuantumCpuLogic) (Object) this).getHostBlockEntity();
    }
}
