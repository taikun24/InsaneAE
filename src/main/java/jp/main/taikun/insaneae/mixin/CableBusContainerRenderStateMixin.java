package jp.main.taikun.insaneae.mixin;

import appeng.api.parts.IPart;
import appeng.client.render.cablebus.CableBusRenderState;
import appeng.parts.CableBusContainer;
import jp.main.taikun.insaneae.client.cable.InsaneCableRenderState;
import jp.main.taikun.insaneae.client.cable.InsaneCableSprites;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 組み上がったレンダーステートに<b>ケーブルの種類</b>を書き込む。
 *
 * <p>ここが「ワールドの描画がこの Mod のケーブルだと気付く」唯一の場所。
 * AE2 はこの後 {@code AECableType} と色しか見ないので、
 * <b>種類はここで拾っておかないと二度と分からない</b>。</p>
 */
@Mixin(value = CableBusContainer.class, remap = false)
public abstract class CableBusContainerRenderStateMixin {

    /** 中央の部品 (= ケーブル) は {@code null} を渡すと返る。 */
    @Shadow
    public abstract IPart getPart(@Nullable Direction partLocation);

    @Inject(method = "getRenderState", at = @At("RETURN"))
    private void insaneae$markCableKind(CallbackInfoReturnable<CableBusRenderState> cir) {
        InsaneCableSprites.Kind kind = InsaneCableSprites.kindOf(getPart(null));
        if (kind != null) {
            ((InsaneCableRenderState) (Object) cir.getReturnValue()).insaneae$setCableKind(kind);
        }
    }
}
