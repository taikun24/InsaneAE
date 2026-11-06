package jp.main.taikun.insaneae.integration.jei;

import appeng.recipes.handlers.ChargerRecipe;
import jp.main.taikun.insaneae.InsaneAE;
import jp.main.taikun.insaneae.registries.ModBlocks;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import net.minecraft.resources.ResourceLocation;

/**
 * AE2 の JEI 連携のチャージャーのカテゴリに、改良チャージャーを触媒として足す。
 *
 * <p>1.20.1 の AE2 15.x は JEI 連携を持っているので、刻印機 (超次元プロセッサ) や
 * チャージャーのレシピは AE2 が出す。こちらは改良チャージャーが同じレシピを使うことを
 * 示すだけ。カテゴリは AE2 の {@code ChargerCategory.RECIPE_TYPE} と同じ
 * {@code ae2:charger} を名前で指す (AE2 の内部クラスに触らないため)。
 * (1.21.1 の AE2 19.x は JEI 連携が無いので、あちらはカテゴリごと自前で出している。)</p>
 */
@JeiPlugin
public class ImprovedChargerJeiPlugin implements IModPlugin {

    private static final RecipeType<ChargerRecipe> AE2_CHARGER =
            RecipeType.create("ae2", "charger", ChargerRecipe.class);

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID, "improved_charger");
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(ModBlocks.IMPROVED_CHARGER.get(), AE2_CHARGER);
    }
}
