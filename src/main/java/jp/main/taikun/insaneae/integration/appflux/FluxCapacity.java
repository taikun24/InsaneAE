package jp.main.taikun.insaneae.integration.appflux;

import com.glodblock.github.appflux.common.me.key.type.FluxKeyType;

/**
 * FE セルの容量 (バイト) を、AppliedFlux の計算が long を溢れない範囲に収める。
 *
 * <p>AppliedFlux のセル ({@code FluxCellInventory}) は FE の上限を
 * {@code getBytes() * FluxKeyType.getAmountPerByte()} と<b>素の long の掛け算</b>で出し、
 * 使用バイトも {@code (stored + perByte - 1) / perByte} で出す。1 バイトあたりの FE は
 * AppliedFlux の設定 ({@code flux_cell.amount}、既定 1,048,576 = 2^20) なので、
 * 既定のままだと 2^43 バイト (8T) で上限が負に回り、セルが「満杯で何も入らない」状態になる。</p>
 *
 * <p>階層側 ({@link FluxTiers}) は 8T までに抑えてあるが、既定設定でも 8T は long を
 * ちょうど 1 越え、設定で 1 バイトあたりの FE を上げられるともっと手前で溢れる。そこで<b>上限 FE が
 * {@code Long.MAX_VALUE - perByte} を超えないバイト数</b>で頭打ちにする
 * (使用バイトの式の {@code + perByte - 1} も溢れない)。</p>
 */
final class FluxCapacity {

    private FluxCapacity() {
    }

    /** {@code bytes} を、FE に直しても long に収まるバイト数に丸める。 */
    static long clampBytes(long bytes) {
        long perByte = Math.max(1, FluxKeyType.TYPE.getAmountPerByte());
        return Math.min(bytes, (Long.MAX_VALUE - perByte) / perByte);
    }
}
