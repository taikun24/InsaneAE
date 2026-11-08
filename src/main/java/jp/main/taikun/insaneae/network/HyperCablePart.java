package jp.main.taikun.insaneae.network;

import appeng.api.util.AEColor;
import appeng.items.parts.ColoredPartItem;
import jp.main.taikun.insaneae.registries.ModParts;

/**
 * 超次元MEケーブル — 細くて部品が貼れて、しかも32本を超えるケーブル。
 *
 * <p>太さ・部品の可否・色塗りは {@link ThinDenseCablePart} と共通で、
 * 違うのは本数だけ。{@code GridNodeChannelMixin} が
 * 「オーナーがこのクラスなら」上限を引き上げる ({@link HyperNetwork})。
 * 置いた時点で、条件なしに常に既定128本運ぶ。</p>
 *
 * <p>Mixinが適用されない場面、たとえば{@code ChannelMode.INFINITE}では
 * 親が立てている{@code DENSE_CAPACITY}の32本に落ちる。{@link CompressedCablePart}と同じ値。</p>
 */
public class HyperCablePart extends ThinDenseCablePart {

    public HyperCablePart(ColoredPartItem<?> partItem) {
        super(partItem);
    }

    @Override
    protected ColoredPartItem<?> itemForColor(AEColor color) {
        return ModParts.hyperCable(color);
    }
}
