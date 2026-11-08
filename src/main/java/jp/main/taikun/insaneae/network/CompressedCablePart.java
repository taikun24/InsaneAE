package jp.main.taikun.insaneae.network;

import appeng.api.util.AEColor;
import appeng.items.parts.ColoredPartItem;
import jp.main.taikun.insaneae.registries.ModParts;

/**
 * 圧縮ME高密度スマートケーブル — 高密度ケーブルを細さそのままに詰め込んだケーブル。
 *
 * <p>本数はAE2の高密度ケーブルと同じ32本。ChannelModeの倍率を掛けた値で、
 * 親 ({@link ThinDenseCablePart}) が立てる {@code DENSE_CAPACITY} がそのまま使われる。
 * このMod側で本数をいじる処理は一切通らない。{@code GridNodeChannelMixin}は
 * {@link HyperCablePart} だけを見ている)。</p>
 *
 * <p>高密度ケーブルとの違いは太さと部品の可否だけ:
 * 細いので隣のブロックと干渉せず、ストレージバスなどを直接貼れる。
 * そのぶんAE2の高密度ケーブルより1段あとの素材で作る ({@code ModRecipeProvider})。</p>
 */
public class CompressedCablePart extends ThinDenseCablePart {

    public CompressedCablePart(ColoredPartItem<?> partItem) {
        super(partItem);
    }

    @Override
    protected ColoredPartItem<?> itemForColor(AEColor color) {
        return ModParts.compressedCable(color);
    }
}
