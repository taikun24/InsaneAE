package jp.main.taikun.insaneae.network;

import appeng.api.util.AEColor;
import appeng.items.parts.ColoredPartItem;
import jp.main.taikun.insaneae.registries.ModParts;

/**
 * 超次元MEケーブル (無制限)。太さ・部品の可否・色塗りは {@link ThinDenseCablePart} と共通で、
 * 違うのはチャンネル数に上限が無いこと。
 *
 * <p>{@code GridNodeChannelMixin} が「オーナーがこのクラスなら」上限を
 * {@code Integer.MAX_VALUE} にする。経路計算は上限を min で削っていくだけなので、
 * この値で経路も表示も追従する。</p>
 */
public class InsaneCablePart extends ThinDenseCablePart {

    public InsaneCablePart(ColoredPartItem<?> partItem) {
        super(partItem);
    }

    @Override
    protected ColoredPartItem<?> itemForColor(AEColor color) {
        return ModParts.insaneCable(color);
    }
}
