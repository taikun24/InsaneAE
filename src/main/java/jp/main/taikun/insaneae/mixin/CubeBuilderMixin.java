package jp.main.taikun.insaneae.mixin;

import jp.main.taikun.insaneae.client.cable.InsaneCableSprites;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * ケーブルの板に貼るスプライトを、出力の直前で差し替える。
 *
 * <p>AE2 の {@code CableBuilder} は帯も芯も目盛りも<b>すべてここを通して</b>貼るので、
 * 呼び出し側 1 つ 1 つに手を入れるより<b>ここ 1 箇所</b>で受ける方が、
 * AE2 の内部が変わっても壊れにくい。</p>
 *
 * <p>効くのは {@code CableBusBakedModelMixin} が印を立てている間だけで、
 * そのときでも<b>対応表に載っているスプライトしか</b>差し替わらない。</p>
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
