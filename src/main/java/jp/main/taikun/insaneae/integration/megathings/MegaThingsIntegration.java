package jp.main.taikun.insaneae.integration.megathings;

import appeng.api.stacks.AEKeyType;
import io.github.lapis256.ae2_mega_things.init.AE2MTItems;
import jp.main.taikun.insaneae.crafting.InsaneCraftingUnitType;
import jp.main.taikun.insaneae.integration.AddonIntegration;
import jp.main.taikun.insaneae.util.TieredNames;
import net.minecraft.resources.ResourceLocation;
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
 * AE2 MEGA Things連携: 1G〜16Pの液体用DISKを足す。MEGA Thingsの256Mの続きになる。
 * アイテム用はAE2 Things連携 ({@code AE2ThingsIntegration}) の方で足している。
 *
 * <p>このクラスはMEGA Thingsのクラスを直接参照するので、名簿がラムダ越しに生成している。</p>
 */
public final class MegaThingsIntegration implements AddonIntegration {

    public static final String MODID = "ae2_mega_things";

    private final MegaDiskSet disks = new MegaDiskSet("fluid_disk_drive_",
            () -> AE2MTItems.INSTANCE.getMEGA_FLUID_DISK_HOUSING(), driveModel("fluid"));

    /** MEドライブに挿したときの見た目。MEGA ThingsのMEGA DISKと同じもの。版でファイル名が違う。 */
    static ResourceLocation driveModel(String type) {
        //? if >=1.21 {
        return ResourceLocation.fromNamespaceAndPath(MODID, "block/drive/cells/256m_" + type + "_cell");
        //?} else {
        /*return ResourceLocation.fromNamespaceAndPath(MODID, "block/drive/cells/mega_" + type);
        *///?}
    }

    @Override
    public String modId() {
        return MODID;
    }

    @Override
    public void registerContent() {
        disks.register((component, housing, bytes, idleDrain, tierLabel) -> new InsaneMegaDiskItem(
                AEKeyType.fluids(), component, housing, bytes, idleDrain,
                TieredNames.FLUID_DISK_DRIVE, tierLabel));
    }

    /** 色付けはAE2の {@code BasicStorageCell} と同じ。MEGA Thingsも自分のDISKにそれを使っている。 */
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
