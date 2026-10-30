package jp.main.taikun.insaneae.mixin.aco;

import appeng.api.stacks.AEKey;
import com.syaru.ae2craftingoptimizer.access.ExtendedAePlusBigIntegerCellInventoryAccess;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import jp.main.taikun.insaneae.integration.aco.InfiniteCellExactState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

import java.math.BigInteger;
import java.util.UUID;

/**
 * ME Infinity Cell を、ACO の exact 実行が材料を引ける BigInteger セルにする (書き込み側)。
 *
 * <p>ACO が在庫を減らすときに見るのはこの内部インターフェイスだけ
 * ({@link UltraCreativeCellExactStorageMixin} と同じ事情)。中身は取り出し放題なので、
 * ACO が Map を書き換えて減らしても実害は無い。</p>
 */
@Pseudo
@Mixin(targets = {
        "net.yxiao233.meinfinitycell.common.inventory.InfinityCellInventory",
        "net.yxiao233.meinfinitycell.common.inventory.InfinitiesCellInventory"
}, remap = false)
public abstract class InfinityCellExactStorageMixin implements ExtendedAePlusBigIntegerCellInventoryAccess {

    @Override
    public Object2ObjectMap<AEKey, BigInteger> aco$getExactStoredAmounts() {
        return insaneae$state().amounts();
    }

    @Override
    public int aco$getExactStoredTypeCount() {
        return insaneae$state().typeCount();
    }

    @Override
    public void aco$setExactStoredTypeCount(int typeCount) {
        insaneae$state().setTypeCount(typeCount);
    }

    @Override
    public BigInteger aco$getExactStoredTotal() {
        return insaneae$state().total();
    }

    @Override
    public void aco$setExactStoredTotal(BigInteger total) {
        insaneae$state().setTotal(total);
    }

    @Override
    public void aco$saveExactChanges() {
        // 保存するものは無い (中身はセルの種類で決まっている)。
    }

    @Override
    public boolean aco$hasExactStorageUuid() {
        return insaneae$state().uuid() != null;
    }

    @Override
    public UUID aco$getExactStorageUuid() {
        return insaneae$state().uuid();
    }

    @Override
    public UUID aco$assignExactStorageUuid() {
        return insaneae$state().assignUuid();
    }

    @Unique
    private InfiniteCellExactState insaneae$state() {
        return ((InfiniteCellExactState.Holder) this).insaneae$infiniteExactState();
    }
}
