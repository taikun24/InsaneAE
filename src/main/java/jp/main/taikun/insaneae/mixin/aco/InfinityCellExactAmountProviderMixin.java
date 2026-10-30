package jp.main.taikun.insaneae.mixin.aco;

import appeng.api.stacks.AEKey;
import com.syaru.ae2craftingoptimizer.api.contract.ExactStorageAmountProvider;
import jp.main.taikun.insaneae.integration.aco.InfiniteCellExactState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

import java.math.BigInteger;
import java.util.Map;

/**
 * ME Infinity Cell の在庫を、ACO の公開契約で BigInteger として名乗る (読み取り側)。
 *
 * <p>ACO 1.5.20 より前には無いインターフェイスなので、{@link AcoMixinPlugin} が実在を
 * 確かめてから当てる ({@link UltraCreativeCellExactAmountProviderMixin} と同じ扱い)。</p>
 */
@Pseudo
@Mixin(targets = {
        "net.yxiao233.meinfinitycell.common.inventory.InfinityCellInventory",
        "net.yxiao233.meinfinitycell.common.inventory.InfinitiesCellInventory"
}, remap = false)
public abstract class InfinityCellExactAmountProviderMixin implements ExactStorageAmountProvider {

    @Override
    public Map<AEKey, BigInteger> exactStoredAmounts() {
        return ((InfiniteCellExactState.Holder) this).insaneae$infiniteExactState().amounts();
    }
}
