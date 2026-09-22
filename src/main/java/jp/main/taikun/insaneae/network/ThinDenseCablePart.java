package jp.main.taikun.insaneae.network;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNodeListener;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.util.AECableType;
import appeng.api.util.AEColor;
import appeng.items.parts.ColoredPartItem;
import appeng.parts.networking.CablePart;
import appeng.parts.networking.IUsedChannelProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;

import java.util.function.Predicate;

/**
 * <b>細いのに高密度、しかも部品が貼れる</b>ケーブルの共通部分。
 *
 * <p>この Mod のケーブルは 2 本ともここを継承する。違うのは<b>チャンネルの本数だけ</b>。</p>
 * <ul>
 *   <li>{@link CompressedCablePart} … 高密度と同じ 32 本 (このクラスのまま)</li>
 *   <li>{@link HyperCablePart} … {@code GridNodeChannelMixin} が上限を引き上げる (既定 128 本)</li>
 * </ul>
 *
 * <h2>AE2 では「太さ」「部品の可否」「本数」が別々の値になっている</h2>
 * <ul>
 *   <li>太さ・接続形状 … {@link #getCableConnectionType()} が返す {@link AECableType}</li>
 *   <li>部品の可否 … {@code IPart#supportsBuses()} が
 *       {@code BusSupport.DENSE_CABLE} か {@code CABLE} か</li>
 *   <li>本数 … ノードの {@link GridFlags#DENSE_CAPACITY}</li>
 * </ul>
 * <p>AE2 の高密度ケーブル ({@code DenseCablePart}) はこの 3 つを全部「高密度側」に倒しているだけで、
 * <b>組み合わせが縛られているわけではない</b>。ここでは
 * 「スマートケーブルの見た目 (細い)」+「{@code CABLE} = 部品が貼れる」+「高密度の本数」を選ぶ。
 * {@link CablePart} の既定が {@code BusSupport.CABLE} なので、そこは何も書かなくてよい。</p>
 *
 * <h2>見た目はスマートケーブルと同じになる</h2>
 * <p>ケーブルの描画は部品のモデルではなく {@code CableBuilder} が持っていて、
 * テクスチャは <b>({@link AECableType}, {@link AEColor}) の組でしか引けない</b>
 * (アドオン側から差し込む口が無い)。したがって<b>ワールド上では 2 本とも
 * AE2 のスマートケーブルと同じ絵</b>になる。見分けるにはアイテム名か、
 * 覗いたときのチャンネル表示 (本数の上限が違う) を見ること。
 * 手持ちの絵だけは自前のテクスチャなので 3 者とも違う ({@code tools/gen_network_textures.py})。</p>
 */
public abstract class ThinDenseCablePart extends CablePart implements IUsedChannelProvider {

    protected ThinDenseCablePart(ColoredPartItem<?> partItem) {
        super(partItem);
        // 高密度ケーブルと同じ 32 本 (× ChannelMode 倍率)。
        getMainNode().setFlags(GridFlags.DENSE_CAPACITY);
    }

    /**
     * 同じ種類の、その色のアイテム。色塗りの差し替え先になる。
     *
     * <p>17 色そろっていないと色塗りが黙って失敗するので、{@code ModParts} 側で
     * 全色を登録しておくこと。</p>
     */
    protected abstract ColoredPartItem<?> itemForColor(AEColor color);

    /**
     * スマートケーブルとして繋ぐ = 細いまま、かつ使用チャンネルが側面に出る。
     *
     * <p>本数の上限は {@code GridNode#getMaxChannels()} 側で決まるので、ここが
     * {@code SMART} でも 128 本運べる。側面の表示は
     * {@code CablePart#getVisualChannels} が「使用数 ÷ 上限」の割合を 8 段階に丸めるため、
     * 上限が 128 でも目盛りは正しく出る。</p>
     */
    @Override
    public AECableType getCableConnectionType() {
        return AECableType.SMART;
    }

    @Override
    public void getBoxes(IPartCollisionHelper bch, Predicate<Direction> connected) {
        updateConnections();
        // AE2 のスマートケーブルと同じ太さ (6/16 角)。細いので部品と共存できる。
        addNonDenseBoxes(bch, connected, 5, 11);
    }

    /** 使用チャンネルの表示を更新する (AE2 のスマートケーブルと同じ)。 */
    @Override
    protected void onMainNodeStateChanged(IGridNodeListener.State reason) {
        if (reason != IGridNodeListener.State.GRID_BOOT) {
            getHost().markForUpdate();
        }
    }

    /**
     * 色塗り (色塗り器 / ペイントボール) で<b>同じ種類の、その色のケーブルに差し替える</b>。
     *
     * <p><b>これを書かないとこのケーブルが黙って普通のスマートケーブルに化ける。</b>
     * {@link CablePart#changeColor} は {@link #getCableConnectionType()} で分岐して
     * {@code AEParts.SMART_CABLE} の同色アイテムに差し替える作りなので、
     * こちらが {@code SMART} を返している以上そのまま素通しさせられない。
     * 差し替え先を {@link #itemForColor} に向け直している。</p>
     *
     * <p>中身は AE2 の実装と同じ手順:
     * <b>アイテムを差し替え → ノードの色を更新 → ホストへ通知</b>。
     * ノードの色 ({@code setGridColor}) は<b>どのケーブルと繋がるか</b>を決めるので、
     * ここを飛ばすと見た目だけ変わって接続規則が元の色のまま残る。</p>
     */
    @Override
    public boolean changeColor(AEColor newColor, Player who) {
        if (getCableColor() == newColor) {
            return false;
        }
        // クライアント側では「色を変えられる」とだけ答え、実際の差し替えはサーバに任せる
        // (AE2 の CablePart と同じ。両側で書き換えると部品の同期がずれる)。
        if (!isClientSide()) {
            setPartItem(itemForColor(newColor));
            getMainNode().setGridColor(getCableColor());
            getHost().partChanged();
            getHost().markForUpdate();
            getHost().markForSave();
        }
        return true;
    }
}
