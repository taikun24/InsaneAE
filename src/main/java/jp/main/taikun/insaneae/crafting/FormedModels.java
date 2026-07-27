package jp.main.taikun.insaneae.crafting;

import net.minecraft.resources.ResourceLocation;

/**
 * formed 状態 (組み上がったマルチブロック) のモデル ID。
 *
 * <p><b>ae2 名前空間で登録しないと動かない。</b>
 * AE2 の {@code BuiltInModelHooks#getBuiltInModel} が先頭で
 * {@code if (!"ae2".equals(variantId.getNamespace())) return null;} と弾いており、
 * {@code insaneae:...} で登録しても差し替えが起きない。すると blockstate が参照している
 * 空モデル {@code {}} がそのまま使われ、<b>組み上げた瞬間にブロックが透明になる</b>。</p>
 *
 * <p>実ファイルは要らない。差し替えは {@code ModelBakery#loadModel} の HEAD で
 * cancel されるので、モデル JSON は読まれない。</p>
 */
public final class FormedModels {

    private static final String AE2 = "ae2";

    private FormedModels() {
    }

    /**
     * @param name 例: {@code "1g_storage"} / {@code "16x_accelerator"}
     */
    public static ResourceLocation of(String name) {
        return ResourceLocation.fromNamespaceAndPath(AE2, "block/crafting/insaneae/" + name + "_formed");
    }
}
