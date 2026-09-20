package jp.main.taikun.insaneae.network;

import appeng.api.networking.pathing.ChannelMode;
import jp.main.taikun.insaneae.config.InsaneAEConfig;

/**
 * 超次元チャンネル (32 本を超える輸送) の判定をここ 1 箇所にまとめる。
 *
 * <h2>AE2 のチャンネル上限がどこにあるか</h2>
 * <p>AE2 19.2 でチャンネルの本数を決めているのは
 * <b>{@code appeng.me.GridNode#getMaxChannels()} ただ 1 箇所</b>で、</p>
 * <pre>
 *   CANNOT_CARRY      → 0                    (コントローラなど「出す側」)
 *   ChannelMode.INFINITE → Integer.MAX_VALUE
 *   DENSE_CAPACITY    → 32 × factor          (高密度ケーブル)
 *   それ以外           → 8 × factor
 * </pre>
 * <p>経路計算 ({@code PathingCalculation#tryUseChannel}) も
 * 部分木の上限 ({@code GridNode#setControllerRoute} の {@code subtreeMaxChannels}) も
 * すべてこの値の {@code min} を取っていくだけなので、<b>ここを上げれば全部が追従する</b>。</p>
 *
 * <p>{@code GridConnection#getMaxChannels()} にも 32 決め打ちがあるが、
 * <b>AE2 19.2 では誰も呼んでいない</b> ({@code IPathItem} の実装として残っているだけで、
 * {@code PathingCalculation} が呼ぶのは {@code GridNode} の方だけ)。
 * 触らない理由がそれで、将来 AE2 が呼び始めたらここにも Mixin が要る。</p>
 *
 * <h2>解錠の条件は無い</h2>
 * <p>本数を増やせるのは<b>ケーブルだけ</b>。コントローラのノードは {@code CANNOT_CARRY} で
 * 上限が常に 0 — チャンネルを運ばない側なので、専用コントローラを足しても本数は 1 本も増えない。
 * したがって「専用コントローラが在るときだけ解錠する」ような条件は持たせず、
 * <b>超次元 ME ケーブルは置いた時点で常に {@link #channelCapacity} 本運ぶ</b>。
 * ネットワークのコントローラは AE2 のもの (または他 Mod のもの) をそのまま使う。</p>
 */
public final class HyperNetwork {

    private HyperNetwork() {
    }

    /**
     * 超次元ケーブル 1 本が運べる本数。
     *
     * <p>{@link ChannelMode} の倍率は AE2 の他のケーブルと同じように掛ける
     * (x2 設定なら高密度が 64 になるのと並びで、超次元も 2 倍になる)。
     * {@code INFINITE} のときは AE2 が先に {@code Integer.MAX_VALUE} を返すので、
     * ここには来ない。</p>
     */
    public static int channelCapacity(ChannelMode mode) {
        return InsaneAEConfig.hyperChannels() * mode.getCableCapacityFactor();
    }
}
