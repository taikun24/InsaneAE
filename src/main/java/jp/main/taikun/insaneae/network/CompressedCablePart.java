package jp.main.taikun.insaneae.network;

import appeng.api.util.AEColor;
import appeng.items.parts.ColoredPartItem;
import jp.main.taikun.insaneae.registries.ModParts;

/**
 * 圧縮 ME 高密度スマートケーブル — 高密度ケーブルを<b>細さそのままに詰め込んだ</b>ケーブル。
 *
 * <p>本数は AE2 の高密度ケーブルと同じ <b>32 本</b> (× ChannelMode 倍率) で、
 * 親 ({@link ThinDenseCablePart}) が立てる {@code DENSE_CAPACITY} がそのまま効く。
 * <b>この Mod 側で本数をいじる処理は一切通らない</b> ({@code GridNodeChannelMixin} は
 * {@link HyperCablePart} だけを見ている)。</p>
 *
 * <p>高密度ケーブルとの違いは<b>太さと部品の可否</b>だけ:
 * 細いので隣のブロックと干渉せず、ストレージバスなどを直接貼れる。
 * そのぶん AE2 の高密度ケーブルより 1 段あとの素材で作る ({@code ModRecipeProvider})。</p>
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
