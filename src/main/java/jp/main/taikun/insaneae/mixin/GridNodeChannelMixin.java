package jp.main.taikun.insaneae.mixin;

import appeng.api.networking.pathing.ChannelMode;
import appeng.me.Grid;
import appeng.me.GridNode;
import jp.main.taikun.insaneae.network.HyperCablePart;
import jp.main.taikun.insaneae.network.HyperNetwork;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 超次元ケーブルのチャンネル上限を 32 から引き上げる。
 *
 * <p>AE2 19.2 でチャンネルの本数を決めているのは {@code GridNode#getMaxChannels()}
 * <b>ただ 1 箇所</b>で、経路計算も部分木の上限もこの値の {@code min} を取っていくだけ。
 * したがってここだけを差し替えれば、経路計算・表示・ツールチップまで全部追従する
 * (どこを見て確かめたかは {@link HyperNetwork} に書いてある)。</p>
 *
 * <h2>効く条件を狭く保つ</h2>
 * <p>{@code getMaxChannels()} は<b>経路計算のたびに全ノードで呼ばれる</b>ので、
 * 最初の {@code instanceof} で落ちる形にしてある。グリッドを引く処理まで進むのは
 * 超次元ケーブルのノードだけ。</p>
 *
 * <p>解錠されていなければ<b>何も書き換えずに AE2 の値を返させる</b> (＝高密度と同じ 32)。
 * 既存のネットワークの挙動を変えないための一番大事な性質なので、
 * ここを「常に 128」にしてはいけない。</p>
 */
@Mixin(value = GridNode.class, remap = false)
public abstract class GridNodeChannelMixin {

    @Shadow
    public abstract Object getOwner();

    /**
     * {@code myGrid} をそのまま返す (未接続なら null)。
     *
     * <p>{@code getGrid()} の方は破棄済みノードで {@code IllegalStateException} を投げるので、
     * 例外を投げない方を使う。</p>
     */
    @Shadow
    public abstract Grid getInternalGrid();

    @Inject(method = "getMaxChannels", at = @At("HEAD"), cancellable = true)
    private void insaneae$hyperCapacity(CallbackInfoReturnable<Integer> cir) {
        if (!(getOwner() instanceof HyperCablePart)) {
            return;
        }
        Grid grid = getInternalGrid();
        if (grid == null) {
            return;
        }
        ChannelMode mode = grid.getPathingService().getChannelMode();
        if (mode == ChannelMode.INFINITE) {
            // 無制限設定では AE2 が Integer.MAX_VALUE を返すので、下げてはいけない。
            return;
        }
        if (!HyperNetwork.isUnlocked(grid)) {
            return;
        }
        cir.setReturnValue(HyperNetwork.channelCapacity(mode));
    }
}
