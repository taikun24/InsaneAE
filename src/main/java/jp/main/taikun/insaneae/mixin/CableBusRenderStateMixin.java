package jp.main.taikun.insaneae.mixin;

import appeng.client.render.cablebus.CableBusRenderState;
import jp.main.taikun.insaneae.client.cable.InsaneCableRenderState;
import jp.main.taikun.insaneae.client.cable.InsaneCableSprites;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * レンダーステートに<b>ケーブルの種類</b>を 1 つ足す。
 *
 * <p>これが無いと、ワールドの絵の差し替え ({@link InsaneCableSprites}) が
 * 「いま描いているのが誰のケーブルか」を知る手段が無い。</p>
 *
 * <h2>equals / hashCode にも混ぜること</h2>
 * <p>{@code CableBusBakedModel} は<b>レンダーステートを鍵にして板をキャッシュ</b>している
 * ({@code LoadingCache})。種類を等価判定に入れないと、色も接続も同じ AE2 のスマートケーブルと
 * <b>同じ板を共有してしまい、片方の絵がもう片方に出る</b>。</p>
 */
@Mixin(value = CableBusRenderState.class, remap = false)
public abstract class CableBusRenderStateMixin implements InsaneCableRenderState {

    @Unique
    private InsaneCableSprites.Kind insaneae$cableKind;

    @Override
    public InsaneCableSprites.Kind insaneae$getCableKind() {
        return insaneae$cableKind;
    }

    @Override
    public void insaneae$setCableKind(InsaneCableSprites.Kind kind) {
        this.insaneae$cableKind = kind;
    }

    @Inject(method = "hashCode", at = @At("RETURN"), cancellable = true)
    private void insaneae$hashKind(CallbackInfoReturnable<Integer> cir) {
        int kind = insaneae$cableKind == null ? 0 : insaneae$cableKind.hashCode();
        cir.setReturnValue(31 * cir.getReturnValueI() + kind);
    }

    @Inject(method = "equals", at = @At("RETURN"), cancellable = true)
    private void insaneae$equalsKind(Object other, CallbackInfoReturnable<Boolean> cir) {
        // AE2 が false と答えたものを true にすることはない。true のときだけ種類を見る。
        if (!cir.getReturnValueZ() || !(other instanceof InsaneCableRenderState state)) {
            return;
        }
        if (state.insaneae$getCableKind() != insaneae$cableKind) {
            cir.setReturnValue(false);
        }
    }
}
