package jp.main.taikun.insaneae.integration.appflux;

import com.glodblock.github.appflux.common.me.key.type.FluxKeyType;

/**
 * FEセルの容量をバイト数で、AppliedFluxの計算がlongを溢れない範囲に収める。
 *
 * <p>AppliedFluxのセル ({@code FluxCellInventory}) はFEの上限を
 * {@code getBytes() * FluxKeyType.getAmountPerByte()} と素のlongの掛け算で出し、
 * 使用バイトも {@code (stored + perByte - 1) / perByte} で出す。1バイトあたりのFEは
 * AppliedFluxの設定、{@code flux_cell.amount}で既定は1,048,576 = 2^20なので、
 * 既定のままだと2^43バイト (8T) で上限が負に回り、セルが「満杯で何も入らない」状態になる。</p>
 *
 * <p>階層側 ({@link FluxTiers}) は8Tまでに抑えてあるが、既定設定でも8Tはlongを
 * ちょうど1越え、設定で1バイトあたりのFEを上げられるともっと手前で溢れる。そこで上限FEが
 * {@code Long.MAX_VALUE - perByte} を超えないバイト数で頭打ちにする
 * 使用バイトの式の{@code + perByte - 1}も溢れない。</p>
 */
final class FluxCapacity {

    private FluxCapacity() {
    }

    /** {@code bytes} を、FEに直してもlongに収まるバイト数に丸める。 */
    static long clampBytes(long bytes) {
        long perByte = Math.max(1, FluxKeyType.TYPE.getAmountPerByte());
        return Math.min(bytes, (Long.MAX_VALUE - perByte) / perByte);
    }
}
