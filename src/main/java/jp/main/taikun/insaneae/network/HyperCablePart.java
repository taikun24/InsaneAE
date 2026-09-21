package jp.main.taikun.insaneae.network;

import appeng.api.util.AEColor;
import appeng.items.parts.ColoredPartItem;
import jp.main.taikun.insaneae.registries.ModParts;

/**
 * 超次元 ME ケーブル — 細くて部品が貼れて、しかも<b>32 本を超える</b>ケーブル。
 *
 * <p>太さ・部品の可否・色塗りは {@link ThinDenseCablePart} と共通で、
 * 違うのは<b>本数だけ</b>。{@code GridNodeChannelMixin} が
 * 「オーナーがこのクラスなら」上限を引き上げる → {@link HyperNetwork}。
 * <b>解錠の条件は無く、置いた時点で常に既定 128 本</b>運ぶ。</p>
 *
 * <p>ミックスインが効かない場面 ({@code ChannelMode.INFINITE} など) では
 * 親が立てている {@code DENSE_CAPACITY} の 32 本 (= {@link CompressedCablePart} と同じ) に落ちる。</p>
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
