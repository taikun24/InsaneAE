package jp.main.taikun.insaneae.network;

import appeng.api.networking.pathing.ChannelMode;
import jp.main.taikun.insaneae.config.InsaneAEConfig;

/**
 * 超次元チャンネル、つまり32本を超える輸送の判定をここ1箇所にまとめる。
 *
 * <h2>AE2のチャンネル上限がどこにあるか</h2>
 * <p>AE2 19.2でチャンネルの本数を決めているのは
 * {@code appeng.me.GridNode#getMaxChannels()} ただ1箇所で、</p>
 * <pre>
 *   CANNOT_CARRY      → 0コントローラなど「出す側」
 *   ChannelMode.INFINITE → Integer.MAX_VALUE
 *   DENSE_CAPACITY    → 32 × factor高密度ケーブル
 *   それ以外           → 8 × factor
 * </pre>
 * <p>経路計算 ({@code PathingCalculation#tryUseChannel}) も
 * 部分木の上限、{@code GridNode#setControllerRoute}の{@code subtreeMaxChannels}も
 * すべてこの値の {@code min} を取っていくだけなので、ここを上げれば全部が追従する。</p>
 *
 * <p>{@code GridConnection#getMaxChannels()} にも32決め打ちがあるが、
 * AE2 19.2では誰も呼んでいない。{@code IPathItem}の実装として残っているだけで、
 * {@code PathingCalculation} が呼ぶのは {@code GridNode} の方だけ)。
 * 触らない理由がそれで、将来AE2が呼び始めたらここにもMixinが要る。</p>
 *
 * <h2>解錠の条件は無い</h2>
 * <p>本数を増やせるのはケーブルだけ。コントローラのノードは {@code CANNOT_CARRY} で
 * 上限が常に0 — チャンネルを運ばない側なので、専用コントローラを足しても本数は1本も増えない。
 * したがって「専用コントローラが在るときだけ解錠する」ような条件は持たせず、
 * 超次元MEケーブルは置いた時点で常に {@link #channelCapacity} 本運ぶ。
 * ネットワークのコントローラはAE2のもの、または他Modのものをそのまま使う。</p>
 */
public final class HyperNetwork {

    private HyperNetwork() {
    }

    /**
     * 超次元ケーブル1本が運べる本数。
     *
     * <p>{@link ChannelMode} の倍率はAE2の他のケーブルと同じように掛ける
     * x2設定なら高密度が64になるのと並びで、超次元も2倍になる。
     * {@code INFINITE} のときはAE2が先に {@code Integer.MAX_VALUE} を返すので、
     * ここには来ない。</p>
     */
    public static int channelCapacity(ChannelMode mode) {
        return InsaneAEConfig.hyperChannels() * mode.getCableCapacityFactor();
    }
}
