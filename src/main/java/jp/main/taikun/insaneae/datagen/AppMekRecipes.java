package jp.main.taikun.insaneae.datagen;

import appeng.core.definitions.AEBlocks;
import gripe._90.megacells.integration.appmek.AppMekItems;
import jp.main.taikun.insaneae.crafting.InsaneCraftingUnitType;
import jp.main.taikun.insaneae.integration.appmek.AppMekCells;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.world.level.ItemLike;

import java.util.function.Consumer;

/**
 * 化学物質セルのレシピ生成。
 *
 * <p>appmek / MEGA Cells の appmek 連携クラスを参照するので、
 * <b>appmek がロードされている場合のみ</b> 呼ぶこと ({@link ModRecipeProvider} が分岐している)。
 * 出力されるレシピ自体も {@code forge:conditional} + {@code forge:mod_loaded} で包まれるため、
 * appmek 未導入のワールドでは無効になる。</p>
 */
final class AppMekRecipes {

    private AppMekRecipes() {
    }

    static void build(Consumer<FinishedRecipe> consumer, InsaneCraftingUnitType tier, ItemLike component) {
        ItemLike housing = AppMekItems.MEGA_CHEMICAL_CELL_HOUSING;

        ModRecipeProvider.conditionalShapeless(consumer,
                AppMekCells.CHEMICAL_CELLS.get(tier).get(), component,
                b -> b.requires(housing).requires(component));

        ModRecipeProvider.conditionalShapeless(consumer,
                AppMekCells.PORTABLE_CHEMICAL_CELLS.get(tier).get(), component,
                b -> b.requires(AEBlocks.CHEST)
                        .requires(component)
                        .requires(AEBlocks.DENSE_ENERGY_CELL)
                        .requires(housing));
    }
}
