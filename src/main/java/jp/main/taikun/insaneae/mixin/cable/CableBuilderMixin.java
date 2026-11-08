package jp.main.taikun.insaneae.mixin.cable;

import jp.main.taikun.insaneae.client.cable.InsaneCableSprites;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;

/**
 * 焼き上げ時に「AE2のスプライト → こちらのスプライト」の対応表を作る。
 *
 * <p>AE2が自分のスプライトを引くのに使っているものと同じ取得関数を借りるので、
 * 対応表の鍵はAE2が実際に描画で使うインスタンスそのものになる
 * だから{@link InsaneCableSprites}側は同一性比較で済む。</p>
 *
 * <p>{@code CableBuilder} はpackage-privateなので {@code targets} で名指しする。
 * リソース再読み込みのたびに作り直されるので、対応表もそのたびに作り直る。</p>
 */
@Mixin(targets = "appeng.client.render.cablebus.CableBuilder", remap = false)
public abstract class CableBuilderMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void insaneae$buildReplacements(Function<Material, TextureAtlasSprite> bakedTextureGetter,
            CallbackInfo ci) {
        InsaneCableSprites.rebuild(bakedTextureGetter);
    }
}
