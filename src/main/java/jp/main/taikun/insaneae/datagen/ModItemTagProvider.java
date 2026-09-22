package jp.main.taikun.insaneae.datagen;

import jp.main.taikun.insaneae.InsaneAE;
import jp.main.taikun.insaneae.registries.ModParts;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.concurrent.CompletableFuture;

/**
 * この Mod のアイテムタグ。
 *
 * <p>いまのところ<b>ケーブルの色をまとめる</b>ためだけにある。17 色を 1 つのタグにしておくと、
 * 染色・色落としのレシピを<b>色ごとに 17 本書かずに済む</b> ({@code ModRecipeProvider})。</p>
 */
public class ModItemTagProvider extends ItemTagsProvider {

    /** 圧縮 ME 高密度スマートケーブル 17 色。 */
    public static final TagKey<Item> COMPRESSED_DENSE_CABLES = tag("compressed_dense_cables");

    /** 超次元 ME ケーブル 17 色。 */
    public static final TagKey<Item> HYPER_CABLES = tag("hyper_cables");

    private static TagKey<Item> tag(String name) {
        return TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID, name));
    }

    public ModItemTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries,
            CompletableFuture<TagLookup<net.minecraft.world.level.block.Block>> blockTags,
            ExistingFileHelper existingFiles) {
        super(output, registries, blockTags, InsaneAE.MODID, existingFiles);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        var compressed = tag(COMPRESSED_DENSE_CABLES);
        ModParts.allCompressedCables().forEach(compressed::add);

        var hyper = tag(HYPER_CABLES);
        ModParts.allHyperCables().forEach(hyper::add);
    }
}
