package jp.main.taikun.insaneae.integration.appmek;

import appeng.core.localization.GuiText;
import jp.main.taikun.insaneae.crafting.InsaneCraftingUnitType;
import jp.main.taikun.insaneae.datagen.ModRecipeProvider;
import jp.main.taikun.insaneae.integration.AddonIntegration;
import jp.main.taikun.insaneae.registries.ModUpgrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;

import java.util.List;
//? if >=1.21 {
import gripe._90.megacells.definition.MEGAItems;
import jp.main.taikun.insaneae.registries.ModCapabilities;
import net.minecraft.data.recipes.RecipeOutput;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
//?}
//? if <1.21 {
/*import gripe._90.megacells.integration.appmek.AppMekItems;
import net.minecraft.data.recipes.FinishedRecipe;
import net.neoforged.neoforge.registries.RegistryObject;
import java.util.function.Consumer;
*///?}

/**
 * Applied Mekanistics 連携: 化学物質セル (通常 / ポータブル) を足す。
 * アイテムの中身は {@link AppMekCells}。
 */
public final class AppMekIntegration implements AddonIntegration {

    public static final String MODID = "appmek";

    @Override
    public String modId() {
        return MODID;
    }

    @Override
    public void registerContent() {
        AppMekCells.register();
    }

    @Override
    public List<Item> storageCells() {
        //? if >=1.21 {
        return AppMekCells.CHEMICAL_CELLS.values().stream().map(DeferredHolder::get).toList();
        //?} else {
        /*return AppMekCells.CHEMICAL_CELLS.values().stream().map(RegistryObject::get).toList();
        *///?}
    }

    @Override
    public List<Item> portableCells() {
        //? if >=1.21 {
        return AppMekCells.PORTABLE_CHEMICAL_CELLS.values().stream().map(DeferredHolder::get).toList();
        //?} else {
        /*return AppMekCells.PORTABLE_CHEMICAL_CELLS.values().stream().map(RegistryObject::get).toList();
        *///?}
    }

    /** appmek が自分のセルにしている登録と同じ (液体セルと同じ顔ぶれ)。 */
    @Override
    public void registerUpgrades() {
        String cells = GuiText.StorageCells.getTranslationKey();
        String portables = GuiText.PortableCells.getTranslationKey();
        storageCells().forEach(cell -> ModUpgrades.addFluidCellCards(cell, cells));
        for (Item cell : portableCells()) {
            ModUpgrades.addFluidCellCards(cell, portables);
            ModUpgrades.addPortableEnergyCards(cell, portables);
        }
    }

    /** ポータブルセルの FE 受け取り口 (本体のポータブルセルと同じ)。 */
    @Override
    //? if >=1.21 {
    public void registerCapabilities(RegisterCapabilitiesEvent event) {
        portableCells().forEach(cell -> ModCapabilities.registerPoweredItem(event, cell));
    }

    @Override
    public void buildRecipes(RecipeOutput output, InsaneCraftingUnitType tier, ItemLike component) {
        ItemLike housing = MEGAItems.MEGA_CHEMICAL_CELL_HOUSING;
    //?} else {
    /*public void buildRecipes(Consumer<FinishedRecipe> output, InsaneCraftingUnitType tier, ItemLike component) {
        ItemLike housing = AppMekItems.MEGA_CHEMICAL_CELL_HOUSING;
    *///?}
        ModRecipeProvider.storageCell(output, AppMekCells.CHEMICAL_CELLS.get(tier).get(), component, housing);
        ModRecipeProvider.portableCell(output, AppMekCells.PORTABLE_CHEMICAL_CELLS.get(tier).get(),
                component, housing);
    }
}
