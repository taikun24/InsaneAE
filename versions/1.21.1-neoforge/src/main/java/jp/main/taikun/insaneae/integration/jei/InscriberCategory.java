package jp.main.taikun.insaneae.integration.jei;

import appeng.core.definitions.AEBlocks;
import appeng.recipes.handlers.InscriberProcessType;
import appeng.recipes.handlers.InscriberRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * 刻印機のレシピ。並びは刻印機の画面と同じで、左に上・下、その間の右に中段、矢印の先に結果。
 *
 * <p>刻印 (inscribe) モードでは上・下の金型が残るので、そのスロットのツールチップに
 * 「消費されない」と出す。押印 (press) モードは全部消費される。</p>
 */
class InscriberCategory implements IRecipeCategory<RecipeHolder<InscriberRecipe>> {

    private final IDrawable icon;

    InscriberCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemLike(AEBlocks.INSCRIBER);
    }

    @Override
    public RecipeType<RecipeHolder<InscriberRecipe>> getRecipeType() {
        return AeRecipesJeiPlugin.INSCRIBER;
    }

    @Override
    public Component getTitle() {
        return AEBlocks.INSCRIBER.asItem().getDescription();
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return 100;
    }

    @Override
    public int getHeight() {
        return 54;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<InscriberRecipe> holder, IFocusGroup focuses) {
        InscriberRecipe recipe = holder.value();
        boolean keepsPresses = recipe.getProcessType() == InscriberProcessType.INSCRIBE;
        press(builder, 1, 1, recipe.getTopOptional(), keepsPresses);
        press(builder, 1, 37, recipe.getBottomOptional(), keepsPresses);
        builder.addSlot(RecipeIngredientRole.INPUT, 25, 19)
                .setStandardSlotBackground()
                .addIngredients(recipe.getMiddleInput());
        builder.addSlot(RecipeIngredientRole.OUTPUT, 79, 19)
                .setOutputSlotBackground()
                .addItemStack(recipe.getResultItem());
    }

    /** 上・下の金型スロット。空でも枠だけは出して、刻印機の画面と形を揃える。 */
    private static void press(IRecipeLayoutBuilder builder, int x, int y, Ingredient ingredient,
            boolean keepsPresses) {
        IRecipeSlotBuilder slot = builder.addSlot(RecipeIngredientRole.INPUT, x, y)
                .setStandardSlotBackground()
                .addIngredients(ingredient);
        if (keepsPresses && !ingredient.isEmpty()) {
            slot.addRichTooltipCallback((view, tooltip) -> tooltip.add(
                    Component.translatable("insaneae.jei.not_consumed").withStyle(ChatFormatting.GRAY)));
        }
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<InscriberRecipe> holder,
            IFocusGroup focuses) {
        builder.addAnimatedRecipeArrow(100).setPosition(47, 18);
    }
}
