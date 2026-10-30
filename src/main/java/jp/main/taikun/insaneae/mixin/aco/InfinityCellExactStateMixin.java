package jp.main.taikun.insaneae.mixin.aco;

import appeng.api.storage.cells.StorageCell;
import jp.main.taikun.insaneae.integration.aco.InfiniteCellExactState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

/**
 * ME Infinity Cell のセル 1 つにつき、ACO へ見せる在庫の状態を 1 つ持たせる。
 *
 * <p>ACO の各インターフェイス ({@link InfinityCellExactStorageMixin} ほか) は ACO の版によって
 * 有る無しが違うので別々の Mixin に分けてあり、状態だけをここで共有する。
 * ME Infinity Cell が居なければ {@code @Pseudo} なので何も起きない。</p>
 */
@Pseudo
@Mixin(targets = {
        "net.yxiao233.meinfinitycell.common.inventory.InfinityCellInventory",
        "net.yxiao233.meinfinitycell.common.inventory.InfinitiesCellInventory"
}, remap = false)
public abstract class InfinityCellExactStateMixin implements InfiniteCellExactState.Holder {

    @Unique
    private InfiniteCellExactState insaneae$exactState;

    @Override
    public synchronized InfiniteCellExactState insaneae$infiniteExactState() {
        if (insaneae$exactState == null) {
            insaneae$exactState = InfiniteCellExactState.of((StorageCell) (Object) this);
        }
        return insaneae$exactState;
    }
}
