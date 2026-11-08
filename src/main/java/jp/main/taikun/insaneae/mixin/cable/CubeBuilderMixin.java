package jp.main.taikun.insaneae.mixin.cable;

import jp.main.taikun.insaneae.client.cable.InsaneCableSprites;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * ケーブルの板に貼るスプライトを、出力の直前で差し替える。
 *
 * <p>AE2の {@code CableBuilder} は帯も芯も目盛りもすべてここを通して貼るので、
 * 呼び出し側1つ1つに手を入れるよりここ1箇所で受ける方が、
 * AE2の内部が変わっても壊れにくい。</p>
 *
 * <p>差し替えが働くのは {@code CableBusBakedModelMixin} が印を立てている間だけで、
 * そのときでも対応表に載っているスプライトしか差し替わらない。</p>
 */
@Mixin(targets = "appeng.client.render.cablebus.CubeBuilder", remap = false)
public abstract class CubeBuilderMixin {

    @ModifyVariable(
            method = "setTexture(Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V",
            at = @At("HEAD"), argsOnly = true)
    private TextureAtlasSprite insaneae$swapCableSprite(TextureAtlasSprite texture) {
        return InsaneCableSprites.swap(texture);
    }
}
