package jp.main.taikun.insaneae.cell;

import appeng.api.storage.cells.ICellHandler;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 強化クリエイティブセルを ME ドライブ等に認識させるハンドラ。
 *
 * <p>{@code StorageCells.addCellHandler} で登録する。判定は自前のアイテムだけなので、
 * AE2 側のハンドラとの登録順は問わない。</p>
 */
public final class InsaneCreativeCellHandler implements ICellHandler {

    public static final InsaneCreativeCellHandler INSTANCE = new InsaneCreativeCellHandler();

    private InsaneCreativeCellHandler() {
    }

    @Override
    public boolean isCell(ItemStack is) {
        return is != null && !is.isEmpty() && is.getItem() instanceof InsaneCreativeCellItem;
    }

    @Nullable
    @Override
    public StorageCell getCellInventory(ItemStack is, @Nullable ISaveProvider host) {
        if (!isCell(is)) {
            return null;
        }
        // 超強化セルは BigInteger 在庫を名乗る別の中身を持つ。
        return is.getItem() instanceof InsaneUltraCreativeCellItem
                ? new InsaneUltraCreativeCellInventory(is)
                : new InsaneCreativeCellInventory(is);
    }
}
