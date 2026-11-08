package jp.main.taikun.insaneae.client;

import appeng.block.crafting.ICraftingUnitType;
import appeng.client.render.crafting.AbstractCraftingUnitModelProvider;
import appeng.client.render.crafting.LightBakedModel;
import jp.main.taikun.insaneae.InsaneAE;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.function.Function;

/**
 * formed状態、つまり組み上がったマルチブロックのモデル。
 *
 * <p>AE2の {@code CraftingUnitModelProvider} は発光テクスチャを
 * {@code CraftingUnitType} のswitchで決め打ちしているので、独自階層では使えない。
 * 枠 (ring/light_base) はAE2のものをそのまま参照し、
 * 発光レイヤだけ階層ごとの色で差し替える。</p>
 *
 * <p>発光テクスチャは {@code tools/gen_crafting_textures.py} が生成する
 * {@code insaneae:block/crafting/<id>_light} 系。</p>
 */
public class InsaneCraftingUnitModelProvider extends AbstractCraftingUnitModelProvider<ICraftingUnitType> {

    private static final Material RING_CORNER = ae2("ring_corner");
    private static final Material RING_SIDE_HOR = ae2("ring_side_hor");
    private static final Material RING_SIDE_VER = ae2("ring_side_ver");
    private static final Material LIGHT_BASE = ae2("light_base");

    private final Material light;

    /**
     * @param lightTexture発光レイヤの名前。例: {@code "block/crafting/1g_storage_light"}
     */
    public InsaneCraftingUnitModelProvider(ICraftingUnitType type, String lightTexture) {
        super(type);
        this.light = new Material(TextureAtlas.LOCATION_BLOCKS,
                ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID, lightTexture));
    }

    /** ここで返したものがテクスチャアトラスに登録される。漏らすと真っ黒になる。 */
    @Override
    public List<Material> getMaterials() {
        return List.of(RING_CORNER, RING_SIDE_HOR, RING_SIDE_VER, LIGHT_BASE, light);
    }

    @Override
    public BakedModel getBakedModel(Function<Material, TextureAtlasSprite> spriteGetter) {
        return new LightBakedModel(
                spriteGetter.apply(RING_CORNER),
                spriteGetter.apply(RING_SIDE_HOR),
                spriteGetter.apply(RING_SIDE_VER),
                spriteGetter.apply(LIGHT_BASE),
                spriteGetter.apply(light));
    }

    private static Material ae2(String name) {
        return new Material(TextureAtlas.LOCATION_BLOCKS,
                ResourceLocation.fromNamespaceAndPath("ae2", "block/crafting/" + name));
    }
}
