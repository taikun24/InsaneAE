package jp.main.taikun.insaneae.mixin;

import appeng.client.render.cablebus.CableBusBakedModel;
import appeng.client.render.cablebus.CableBusRenderState;
import jp.main.taikun.insaneae.client.cable.InsaneCableRenderState;
import jp.main.taikun.insaneae.client.cable.InsaneCableSprites;
import net.minecraft.client.renderer.block.model.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * ケーブル 1 本ぶんの板を組む間だけ、差し替えの印を立てる。
 *
 * <p>印は {@link ThreadLocal} なので、チャンクを並列に組んでも混ざらない。</p>
 *
 * <p><b>入口で必ず立て直している</b> (こちらのケーブルでなければ外す) ので、
 * 途中で例外が飛んで出口を通らなかった場合でも、
 * 次にそのスレッドがケーブルを描く時点で印は正しい状態に戻る。</p>
 */
@Mixin(value = CableBusBakedModel.class, remap = false)
public abstract class CableBusBakedModelMixin {

    @Inject(method = "addCableQuads", at = @At("HEAD"))
    private void insaneae$beginCable(CableBusRenderState renderState, List<BakedQuad> quadsOut,
            CallbackInfo ci) {
        InsaneCableSprites.Kind kind =
                ((InsaneCableRenderState) (Object) renderState).insaneae$getCableKind();
        if (kind != null) {
            InsaneCableSprites.beginCable(kind);
        } else {
            InsaneCableSprites.endCable();
        }
    }

    @Inject(method = "addCableQuads", at = @At("RETURN"))
    private void insaneae$endCable(CableBusRenderState renderState, List<BakedQuad> quadsOut,
            CallbackInfo ci) {
        InsaneCableSprites.endCable();
    }
}
