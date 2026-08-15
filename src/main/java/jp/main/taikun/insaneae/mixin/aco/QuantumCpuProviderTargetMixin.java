package jp.main.taikun.insaneae.mixin.aco;

import com.syaru.ae2craftingoptimizer.api.batch.v2.ProviderOwnedPatternBatchTarget;
import jp.main.taikun.insaneae.quantum.QuantumCpuLogic;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * ACO に「このクラフトプロバイダの実体はこの BlockEntity」と教える。
 *
 * <p>ACO は {@code CraftingService.getProviders(pattern)} で候補を集め、
 * {@code ProviderOwnedPatternBatchTarget} を実装しているものから BlockEntity を引き、
 * それが {@code CraftingTableBatchTarget} なら batch のターゲットにする。
 * つまり<b>この 1 メソッドが無いと Quantum CPU は候補にすら入らない</b>。</p>
 */
@Mixin(QuantumCpuLogic.class)
public abstract class QuantumCpuProviderTargetMixin implements ProviderOwnedPatternBatchTarget {

    @Override
    public BlockEntity aco$getProviderOwnedBatchTarget() {
        return ((QuantumCpuLogic) (Object) this).getHostBlockEntity();
    }
}
