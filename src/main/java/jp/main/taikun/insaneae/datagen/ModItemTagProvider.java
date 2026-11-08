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
import net.minecraft.world.level.block.Block;

/**
 * このModのアイテムタグ。
 *
 * <p>いまのところケーブルの色をまとめるためだけにある。17色を1つのタグにしておくと、
 * 染色・色落としのレシピを色ごとに17本書かずに済む ({@code ModRecipeProvider})。</p>
 */
public class ModItemTagProvider extends ItemTagsProvider {

    /** 圧縮ME高密度スマートケーブル17色。 */
    public static final TagKey<Item> COMPRESSED_DENSE_CABLES = tag("compressed_dense_cables");

    /** 超次元MEケーブル17色。 */
    public static final TagKey<Item> HYPER_CABLES = tag("hyper_cables");

    /** 超次元MEケーブル (無制限) 17色。 */
    public static final TagKey<Item> INSANE_CABLES = tag("insane_cables");

    private static TagKey<Item> tag(String name) {
        return TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID, name));
    }

    public ModItemTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries,
            CompletableFuture<TagLookup<Block>> blockTags,
            ExistingFileHelper existingFiles) {
        super(output, registries, blockTags, InsaneAE.MODID, existingFiles);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        var compressed = tag(COMPRESSED_DENSE_CABLES);
        ModParts.allCompressedCables().forEach(compressed::add);

        var hyper = tag(HYPER_CABLES);
        ModParts.allHyperCables().forEach(hyper::add);

        var insane = tag(INSANE_CABLES);
        ModParts.allInsaneCables().forEach(insane::add);
    }
}
