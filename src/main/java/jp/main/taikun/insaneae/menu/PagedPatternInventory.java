package jp.main.taikun.insaneae.menu;

import appeng.api.inventories.InternalInventory;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * パターン枠 1 ページぶんの「窓」。中身は本体のインベントリそのままで、
 * <b>どのページを覗いているかだけを差し替える</b>。
 *
 * <h2>なぜ要るか</h2>
 * <p>Quantum CPU のパターン枠は {@value jp.main.taikun.insaneae.quantum.QuantumCpuBlockEntity#PATTERN_SLOTS} 個ある。
 * これを全部メニューのスロットとして並べると、バニラの
 * {@code AbstractContainerMenu#broadcastChanges} が<b>毎 tick 全スロットを
 * {@code ItemStack.matches} で比較</b>する。パターンは NBT が大きいので、
 * GUI を開いている 1 人につき「1620 枠ぶんの NBT 深い比較 × 2 (lastSlots / remoteSlots)」が
 * 毎 tick サーバスレッドに乗ることになる。</p>
 *
 * <p>そこでメニューには 1 ページぶん (54 枠) だけ置き、ページ送りは
 * この窓の {@link #setPage} を動かして中身を差し替える形にする。同期コストは 1/30 になる。</p>
 *
 * <p>窓はメニュー 1 つにつき 1 個なので、同じ Quantum CPU を複数人が開いても
 * ページは各自独立している。</p>
 */
public final class PagedPatternInventory implements InternalInventory {

    private final InternalInventory backing;
    private final int pageSize;

    private int page;

    public PagedPatternInventory(InternalInventory backing, int pageSize) {
        this.backing = backing;
        this.pageSize = pageSize;
    }

    /** 本体 (全ページぶん) のインベントリ。ページを跨ぐ操作はこちらを直接使う。 */
    public InternalInventory getBacking() {
        return backing;
    }

    public int getPageCount() {
        return Math.max(1, (backing.size() + pageSize - 1) / pageSize);
    }

    public int getPage() {
        return page;
    }

    /** 覗くページを変える。範囲外は丸める。 */
    public void setPage(int page) {
        this.page = Mth.clamp(page, 0, getPageCount() - 1);
    }

    /** 窓のスロット番号 → 本体のスロット番号。本体の範囲外なら -1。 */
    private int backingSlot(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= pageSize) {
            return -1;
        }
        int index = page * pageSize + slotIndex;
        return index < backing.size() ? index : -1;
    }

    @Override
    public int size() {
        return pageSize;
    }

    @Override
    public ItemStack getStackInSlot(int slotIndex) {
        int index = backingSlot(slotIndex);
        return index < 0 ? ItemStack.EMPTY : backing.getStackInSlot(index);
    }

    @Override
    public void setItemDirect(int slotIndex, ItemStack stack) {
        int index = backingSlot(slotIndex);
        if (index >= 0) {
            backing.setItemDirect(index, stack);
        }
    }

    @Override
    public boolean isItemValid(int slotIndex, ItemStack stack) {
        int index = backingSlot(slotIndex);
        return index >= 0 && backing.isItemValid(index, stack);
    }

    @Override
    public int getSlotLimit(int slotIndex) {
        int index = backingSlot(slotIndex);
        return index < 0 ? 0 : backing.getSlotLimit(index);
    }
}
