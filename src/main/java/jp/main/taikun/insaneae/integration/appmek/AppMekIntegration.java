package jp.main.taikun.insaneae.integration.appmek;

import appeng.core.localization.GuiText;
import gripe._90.megacells.integration.appmek.AppMekItems;
import jp.main.taikun.insaneae.crafting.InsaneCraftingUnitType;
import jp.main.taikun.insaneae.datagen.ModRecipeProvider;
import jp.main.taikun.insaneae.integration.AddonIntegration;
import jp.main.taikun.insaneae.registries.ModUpgrades;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.function.Consumer;

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
        return AppMekCells.CHEMICAL_CELLS.values().stream().map(RegistryObject::get).toList();
    }

    @Override
    public List<Item> portableCells() {
        return AppMekCells.PORTABLE_CHEMICAL_CELLS.values().stream().map(RegistryObject::get).toList();
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

    @Override
    public void buildRecipes(Consumer<FinishedRecipe> output, InsaneCraftingUnitType tier, ItemLike component) {
        ItemLike housing = AppMekItems.MEGA_CHEMICAL_CELL_HOUSING;
        ModRecipeProvider.storageCell(output, AppMekCells.CHEMICAL_CELLS.get(tier).get(), component, housing);
        ModRecipeProvider.portableCell(output, AppMekCells.PORTABLE_CHEMICAL_CELLS.get(tier).get(),
                component, housing);
    }
}
