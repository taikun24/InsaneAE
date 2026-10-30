package jp.main.taikun.insaneae.mixin.aco;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.storage.cells.StorageCell;
import com.syaru.ae2craftingoptimizer.api.vector.ExactVectorStoragePolicy;
import jp.main.taikun.insaneae.cell.InsaneUltraCreativeCellInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

import java.math.BigInteger;

/**
 * ME Infinity Cell が exact 実行の納品を受けるかどうか。
 *
 * <p>AE2 の {@code insert} と同じ規約にする。セルの種類と同じものは全部受け入れて消し、
 * それ以外は受けない。量は在庫として名乗っている桁に抑える
 * ({@link UltraCreativeCellExactInsertPolicyMixin} と同じ理由)。</p>
 *
 * <p>ACO 1.5.23 より前には無いインターフェイスなので、{@link AcoMixinPlugin} が実在を確かめてから当てる。</p>
 */
@Pseudo
@Mixin(targets = {
        "net.yxiao233.meinfinitycell.common.inventory.InfinityCellInventory",
        "net.yxiao233.meinfinitycell.common.inventory.InfinitiesCellInventory"
}, remap = false)
public abstract class InfinityCellExactInsertPolicyMixin implements ExactVectorStoragePolicy {

    @Override
    public BigInteger acoMaximumExactInsert(AEKey key, BigInteger currentAmount) {
        boolean accepts = ((StorageCell) (Object) this).insert(key, 1, Actionable.SIMULATE, null) > 0;
        return accepts ? InsaneUltraCreativeCellInventory.exactAmount() : BigInteger.ZERO;
    }
}
