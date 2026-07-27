package jp.main.taikun.insaneae.cell;

import net.minecraft.world.item.ItemStack;

/**
 * AE2 の {@code IBasicCellItem.getBytes()} が {@code int} 返しのため 2 GiB で頭打ちになる問題を回避する、
 * long 容量を持つストレージセルの目印。
 *
 * <p>AE2 の {@code BasicCellInventory} は容量計算自体は {@code long} で行っており、
 * {@code getTotalBytes()} だけが {@code cellType.getBytes(stack)} を {@code long} に広げている。
 * {@code BasicCellInventoryMixin} がその 1 点を差し替えることで、AE2 のセル実装
 * (入出庫・NBT・アップグレード・パーティション・ツールチップ) をそのまま使いつつ
 * 2 GiB 超の容量を実現する。</p>
 */
public interface IHugeCellItem {

    /** このセルの実容量 (バイト)。 */
    long getTotalBytesLong(ItemStack stack);
}
