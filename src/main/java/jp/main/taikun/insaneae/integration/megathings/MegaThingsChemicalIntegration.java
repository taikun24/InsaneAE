package jp.main.taikun.insaneae.integration.megathings;

import io.github.lapis256.ae2_mega_things.integration.appmek.init.AMIntegrationItems;
import jp.main.taikun.insaneae.crafting.InsaneCraftingUnitType;
import jp.main.taikun.insaneae.integration.AddonIntegration;
import jp.main.taikun.insaneae.integration.appmek.AppMekIntegration;
import jp.main.taikun.insaneae.util.TieredNames;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;

import java.util.List;
//? if >=1.21 {
import net.minecraft.data.recipes.RecipeOutput;
//?}
//? if <1.21 {
/*import net.minecraft.data.recipes.FinishedRecipe;
import java.util.function.Consumer;
*///?}

/**
 * AE2 MEGA Things + Applied Mekanistics連携: 1G〜16Pの化学物質用DISKを足す。
 * ハウジングはMEGA Thingsがappmek導入時にだけ登録するので、両方揃っているときだけ働く。
 */
public final class MegaThingsChemicalIntegration implements AddonIntegration {

    private final MegaDiskSet disks = new MegaDiskSet("chemical_disk_drive_",
            () -> AMIntegrationItems.INSTANCE.getMEGA_CHEMICAL_DISK_HOUSING(),
            MegaThingsIntegration.driveModel("chemical"));

    @Override
    public String modId() {
        return MegaThingsIntegration.MODID;
    }

    @Override
    public List<String> requiredMods() {
        return List.of(MegaThingsIntegration.MODID, AppMekIntegration.MODID);
    }

    @Override
    public void registerContent() {
        disks.register((component, housing, bytes, idleDrain, tierLabel) -> new InsaneChemicalDiskItem(
                component, housing, bytes, idleDrain, TieredNames.CHEMICAL_DISK_DRIVE, tierLabel));
    }

    @Override
    public List<Item> storageCells() {
        return disks.items();
    }

    @Override
    public void registerUpgrades() {
        disks.registerUpgrades();
    }

    @Override
    //? if >=1.21 {
    public void buildRecipes(RecipeOutput output, InsaneCraftingUnitType tier, ItemLike component) {
    //?} else {
    /*public void buildRecipes(Consumer<FinishedRecipe> output, InsaneCraftingUnitType tier, ItemLike component) {
    *///?}
        disks.buildRecipe(output, tier, component);
    }
}
