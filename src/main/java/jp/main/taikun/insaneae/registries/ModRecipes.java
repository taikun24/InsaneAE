package jp.main.taikun.insaneae.registries;

import jp.main.taikun.insaneae.InsaneAE;
import jp.main.taikun.insaneae.cell.EmptyCellShapedRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
//? if >=1.21 {
import net.neoforged.neoforge.registries.DeferredHolder;
//?}
//? if <1.21 {
/*import net.neoforged.neoforge.registries.RegistryObject;
*///?}

public final class ModRecipes {
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, InsaneAE.MODID);

    //? if >=1.21 {
    public static final DeferredHolder<RecipeSerializer<?>, EmptyCellShapedRecipe.Serializer> EMPTY_CELL_SHAPED =
    //?} else {
    /*public static final RegistryObject<EmptyCellShapedRecipe.Serializer> EMPTY_CELL_SHAPED =
    *///?}
            SERIALIZERS.register("empty_cell_shaped", EmptyCellShapedRecipe.Serializer::new);

    private ModRecipes() {
    }

    public static void register(IEventBus bus) {
        SERIALIZERS.register(bus);
    }
}
