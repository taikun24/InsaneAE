package jp.main.taikun.insaneae.network;

import appeng.api.networking.IGrid;
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
 * <h2>「コントローラを強くする」が成立しない理由</h2>
 * <p>コントローラのノードは {@code CANNOT_CARRY} なので上限は常に 0 —
 * チャンネルを<b>運ばない</b>側で、運ぶのは常にケーブルだから、
 * コントローラ側の数字をいくら上げても本数は 1 本も増えない。
 * そこで {@link HyperControllerBlockEntity} は<b>本数そのものではなく解錠キー</b>として扱い、
 * 「ネットワークに超次元コントローラが在るときだけ超次元ケーブルが 32 を超える」形にした。
 * ケーブル単体では高密度ケーブルと同じ 32 本のままなので、
 * 既存のネットワークに置いても挙動が変わらない。</p>
 */
public final class HyperNetwork {

    private HyperNetwork() {
    }

    /**
     * この {@code grid} で超次元チャンネルが解錠されているか
     * (＝{@link HyperControllerBlockEntity} が 1 つでも繋がっているか)。
     *
     * <p>{@code getActiveMachines} ではなく {@code getMachines} で見ている。
     * 経路計算はネットワーク起動の途中で走るので、「電力が入って
     * チャンネルが割り当たった後」を条件にすると<b>解錠が 1 テンポ遅れて
     * 経路が 32 本で組まれてしまう</b>。どのみち電力が無ければ
     * チャンネルは流れないので、在るかどうかだけを見れば足りる。</p>
     */
    public static boolean isUnlocked(IGrid grid) {
        return !grid.getMachines(HyperControllerBlockEntity.class).isEmpty();
    }

    /**
     * 解錠済みの超次元ケーブル 1 本が運べる本数。
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
