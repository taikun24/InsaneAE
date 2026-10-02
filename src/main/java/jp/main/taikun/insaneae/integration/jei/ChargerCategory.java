package jp.main.taikun.insaneae.integration.jei;

import appeng.core.definitions.AEBlocks;
import appeng.recipes.handlers.ChargerRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeHolder;

/** チャージャーのレシピ (入力 → 結果)。改良チャージャーも同じレシピを使う。 */
class ChargerCategory implements IRecipeCategory<RecipeHolder<ChargerRecipe>> {

    private final IDrawable icon;

    ChargerCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemLike(AEBlocks.CHARGER);
    }

    @Override
    public RecipeType<RecipeHolder<ChargerRecipe>> getRecipeType() {
        return AeRecipesJeiPlugin.CHARGER;
    }

    @Override
    public Component getTitle() {
        return AEBlocks.CHARGER.asItem().getDescription();
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return 82;
    }

    @Override
    public int getHeight() {
        return 18;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<ChargerRecipe> holder, IFocusGroup focuses) {
        ChargerRecipe recipe = holder.value();
        builder.addSlot(RecipeIngredientRole.INPUT, 1, 1)
                .setStandardSlotBackground()
                .addIngredients(recipe.ingredient);
        builder.addSlot(RecipeIngredientRole.OUTPUT, 61, 1)
                .setOutputSlotBackground()
                .addItemStack(recipe.result);
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<ChargerRecipe> holder,
            IFocusGroup focuses) {
        builder.addAnimatedRecipeArrow(60).setPosition(28, 0);
    }
}
