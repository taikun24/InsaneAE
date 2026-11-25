package jp.main.taikun.insaneae.cell;

import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.StorageCell;
import jp.main.taikun.insaneae.registries.ModRecipes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
//? if >=1.21 {
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.CraftingInput;
//?}
//? if <1.21 {
/*import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
*///?}

/**
 * 材料のセルが空のときだけ成り立つ定形レシピ。
 *
 * <p>セルを材料にすると、中身があってもバニラのクラフトはそのまま消費する。中身はUUIDで
 * ワールドに残るが、ゲーム内からはどこにも繋がらなくなるので、中身のあるセルでは組ませない。</p>
 *
 * <p>{@link ShapedRecipe} を継承しているので、JEIなどには普通の定形レシピとして並ぶ。</p>
 */
public class EmptyCellShapedRecipe extends ShapedRecipe {

    //? if >=1.21 {
    public EmptyCellShapedRecipe(ShapedRecipe shaped) {
        super(shaped.getGroup(), shaped.category(), shaped.pattern, shaped.getResultItem(null),
                shaped.showNotification());
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (!super.matches(input, level)) {
            return false;
        }
        for (int slot = 0; slot < input.size(); slot++) {
            if (holdsContents(input.getItem(slot))) {
                return false;
            }
        }
        return true;
    }
    //?} else {
    /*public EmptyCellShapedRecipe(ShapedRecipe shaped) {
        super(shaped.getId(), shaped.getGroup(), shaped.category(), shaped.getWidth(), shaped.getHeight(),
                shaped.getIngredients(), shaped.getResultItem(null), shaped.showNotification());
    }

    @Override
    public boolean matches(CraftingContainer input, Level level) {
        if (!super.matches(input, level)) {
            return false;
        }
        for (int slot = 0; slot < input.getContainerSize(); slot++) {
            if (holdsContents(input.getItem(slot))) {
                return false;
            }
        }
        return true;
    }
    *///?}

    /** クライアントでもアイテム側の要約から判定できるので、レシピ本の表示とも食い違わない。 */
    private static boolean holdsContents(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        StorageCell cell = StorageCells.getCellInventory(stack, null);
        return cell != null && cell.getStatus() != CellState.EMPTY;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.EMPTY_CELL_SHAPED.get();
    }

    public static final class Serializer implements RecipeSerializer<EmptyCellShapedRecipe> {

        //? if >=1.21 {
        private static final MapCodec<EmptyCellShapedRecipe> CODEC =
                ShapedRecipe.Serializer.CODEC.xmap(EmptyCellShapedRecipe::new, recipe -> recipe);
        private static final StreamCodec<RegistryFriendlyByteBuf, EmptyCellShapedRecipe> STREAM_CODEC =
                ShapedRecipe.Serializer.STREAM_CODEC.map(EmptyCellShapedRecipe::new, recipe -> recipe);

        @Override
        public MapCodec<EmptyCellShapedRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, EmptyCellShapedRecipe> streamCodec() {
            return STREAM_CODEC;
        }
        //?} else {
        /*@Override
        public EmptyCellShapedRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new EmptyCellShapedRecipe(RecipeSerializer.SHAPED_RECIPE.fromJson(id, json));
        }

        @Override
        public EmptyCellShapedRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new EmptyCellShapedRecipe(RecipeSerializer.SHAPED_RECIPE.fromNetwork(id, buffer));
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, EmptyCellShapedRecipe recipe) {
            RecipeSerializer.SHAPED_RECIPE.toNetwork(buffer, recipe);
        }
        *///?}
    }
}
