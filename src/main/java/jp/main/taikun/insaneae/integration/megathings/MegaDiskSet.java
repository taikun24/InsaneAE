package jp.main.taikun.insaneae.integration.megathings;

import appeng.api.client.StorageCellModels;
import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import jp.main.taikun.insaneae.crafting.InsaneCraftingUnitType;
import jp.main.taikun.insaneae.datagen.ModRecipeProvider;
import jp.main.taikun.insaneae.registries.ModCells;
import jp.main.taikun.insaneae.registries.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
//? if >=1.21 {
import net.minecraft.data.recipes.RecipeOutput;
import net.neoforged.neoforge.registries.DeferredHolder;
//?}
//? if <1.21 {
/*import net.minecraft.data.recipes.FinishedRecipe;
import net.neoforged.neoforge.registries.RegistryObject;
import java.util.function.Consumer;
*///?}

/** 液体・化学物質で共通の、1種類ぶんのDISK一式 (1G〜16P)。登録・カード・レシピの中身。 */
final class MegaDiskSet {

    /** これより上の階層は、中身の合計 (mB) がlongを越えるので作らない。 */
    static final InsaneCraftingUnitType TOP_TIER = InsaneCraftingUnitType.STORAGE_16P;

    interface Factory {
        Item create(ItemLike component, ItemLike housing, long bytes, double idleDrain, String tierLabel);
    }

    private final String idPrefix;
    private final Supplier<? extends ItemLike> housing;
    private final ResourceLocation driveModel;
    //? if >=1.21 {
    private final Map<InsaneCraftingUnitType, DeferredHolder<Item, Item>> disks =
    //?} else {
    /*private final Map<InsaneCraftingUnitType, RegistryObject<Item>> disks =
    *///?}
            new EnumMap<>(InsaneCraftingUnitType.class);

    /**
     * @param housing MEGA ThingsのMEGAハウジング。相手の登録が済んでから引くのでSupplierで受ける
     * @param driveModel MEドライブに挿したときの見た目。MEGA Thingsのものを借りる
     */
    MegaDiskSet(String idPrefix, Supplier<? extends ItemLike> housing, ResourceLocation driveModel) {
        this.idPrefix = idPrefix;
        this.housing = housing;
        this.driveModel = driveModel;
    }

    void register(Factory factory) {
        for (InsaneCraftingUnitType tier : InsaneCraftingUnitType.values()) {
            if (tier.compareTo(TOP_TIER) > 0) {
                break;
            }
            ItemLike component = () -> ModItems.CELL_COMPONENTS.get(tier).get();
            ItemLike housingItem = () -> housing.get().asItem();
            disks.put(tier, ModCells.register(idPrefix + tier.id(),
                    () -> factory.create(component, housingItem, tier.getStorageBytes(),
                            ModCells.idleDrain(tier), tier.label())));
        }
    }

    List<Item> items() {
        return disks.values().stream().map(disk -> (Item) disk.get()).toList();
    }

    /** MEGA Thingsが自分のDISKにしている登録と同じ。液体・化学物質はあいまい検索が無いので反転カードだけ。 */
    void registerUpgrades() {
        for (Item disk : items()) {
            Upgrades.add(AEItems.INVERTER_CARD, disk, 1, "text.ae2things.disk_drives");
            StorageCellModels.registerModel(disk, driveModel);
        }
    }

    /** DISKハウジング + セルコンポーネント。分解でもこの2つに戻る。 */
    //? if >=1.21 {
    void buildRecipe(RecipeOutput output, InsaneCraftingUnitType tier, ItemLike component) {
    //?} else {
    /*void buildRecipe(Consumer<FinishedRecipe> output, InsaneCraftingUnitType tier, ItemLike component) {
    *///?}
        var disk = disks.get(tier);
        if (disk == null) {
            return;
        }
        ModRecipeProvider.shapeless(output, disk.get(), component,
                builder -> builder.requires(housing.get()).requires(component));
    }
}
