package jp.main.taikun.insaneae.cell;

import appeng.api.storage.cells.ICellHandler;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.item.ItemStack;

/**
 * 超次元MEセルをMEドライブ等に認識させるハンドラ。
 *
 * <p>{@code StorageCells.addCellHandler} で登録する。判定は自前のアイテムだけなので、
 * AE2側のハンドラとの登録順は問わない。</p>
 */
public final class InsaneMeCellHandler implements ICellHandler {

    public static final InsaneMeCellHandler INSTANCE = new InsaneMeCellHandler();

    private InsaneMeCellHandler() {
    }

    @Override
    public boolean isCell(ItemStack is) {
        return is != null && !is.isEmpty() && is.getItem() instanceof InsaneMeCellItem;
    }

    @Nullable
    @Override
    public StorageCell getCellInventory(ItemStack is, @Nullable ISaveProvider host) {
        if (!isCell(is)) {
            return null;
        }
        return new InsaneMeCellInventory(is, host);
    }
}
