package jp.main.taikun.insaneae.integration.jei;

import appeng.core.definitions.AEBlocks;
import appeng.recipes.AERecipeTypes;
import appeng.recipes.handlers.ChargerRecipe;
import appeng.recipes.handlers.InscriberRecipe;
import jp.main.taikun.insaneae.InsaneAE;
import jp.main.taikun.insaneae.registries.ModBlocks;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.fml.ModList;

/**
 * AE2 の刻印機・チャージャーのレシピを JEI に出す。
 *
 * <p><b>AE2 19.x (1.21.1) は JEI 連携を持っていない</b> (EMI / REI だけ。jar に
 * {@code integration/modules/jei} が無い)。JEI だけの環境では刻印機のカテゴリ自体が無く、
 * AE2 自身のプロセッサも InsaneAE の Insane プロセッサも、レシピをどこからも引けない。
 * Insane プロセッサは AE2 のプロセッサが前提なので、自分のぶんだけでなく
 * <b>AE2 の刻印機・チャージャーのレシピを全部</b>出す。</p>
 *
 * <p><b>EMI が入っているときは何もしない。</b>EMI は AE2 のレシピを自前で出すうえに
 * JEI のプラグインも読み込むので、こちらも出すと同じカテゴリが 2 つ並ぶ。
 * (1.20.1 の AE2 15.x は JEI 連携を持っているので、main ブランチにはこのクラスは無い。)</p>
 */
@JeiPlugin
public class AeRecipesJeiPlugin implements IModPlugin {

    static final RecipeType<RecipeHolder<InscriberRecipe>> INSCRIBER =
            RecipeType.createRecipeHolderType(ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID, "inscriber"));
    static final RecipeType<RecipeHolder<ChargerRecipe>> CHARGER =
            RecipeType.createRecipeHolderType(ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID, "charger"));

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID, "ae2_recipes");
    }

    /** EMI があれば AE2 のレシピはそちらが出す (二重表示を避ける)。 */
    private static boolean enabled() {
        return !ModList.get().isLoaded("emi");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        if (!enabled()) {
            return;
        }
        var guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new InscriberCategory(guiHelper), new ChargerCategory(guiHelper));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (!enabled() || Minecraft.getInstance().level == null) {
            return;
        }
        RecipeManager recipes = Minecraft.getInstance().level.getRecipeManager();
        registration.addRecipes(INSCRIBER, recipes.getAllRecipesFor(AERecipeTypes.INSCRIBER));
        registration.addRecipes(CHARGER, recipes.getAllRecipesFor(AERecipeTypes.CHARGER));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        if (!enabled()) {
            return;
        }
        registration.addRecipeCatalysts(INSCRIBER, AEBlocks.INSCRIBER);
        registration.addRecipeCatalysts(CHARGER, AEBlocks.CHARGER, ModBlocks.IMPROVED_CHARGER.get());
    }
}
