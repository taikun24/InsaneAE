package jp.main.taikun.insaneae.integration.ae2things;

import appeng.api.client.StorageCellModels;
import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import io.github.projectet.ae2things.item.AETItems;
import io.github.projectet.ae2things.item.DISKDrive;
import jp.main.taikun.insaneae.crafting.InsaneCraftingUnitType;
import jp.main.taikun.insaneae.datagen.ModRecipeProvider;
import jp.main.taikun.insaneae.integration.AddonIntegration;
import jp.main.taikun.insaneae.registries.ModCells;
import jp.main.taikun.insaneae.registries.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToIntBiFunction;
//? if >=1.21 {
import net.minecraft.data.recipes.RecipeOutput;
import net.neoforged.neoforge.registries.DeferredHolder;
//?}
//? if <1.21 {
/*import net.minecraft.data.recipes.FinishedRecipe;
import net.neoforged.neoforge.registries.RegistryObject;
import java.util.function.Consumer;
*///?}

/**
 * AE2 Things連携: 1G〜8Eのアイテム用DISKを足す。AE2 Thingsの256k、AE2 MEGA Thingsの256Mの続きになる。
 *
 * <p>DISKは種類の上限が無い代わりに、通常のセルの1/8しか入らない。アイテム1個が1バイトになる。
 * 中身はAE2 ThingsがUUIDごとにワールドへ保存する。液体・化学物質のDISKはAE2 Things本体に
 * 無い。絵も無いので足していない。</p>
 *
 * <p>このクラスはAE2 Thingsのクラスを直接参照するので、ae2thingsが無い環境では
 * ロードしてはいけない。名簿の{@code AddonIntegrations}がラムダ越しに生成している。</p>
 */
public final class AE2ThingsIntegration implements AddonIntegration {

    public static final String MODID = "ae2things";

    /** MEドライブに挿したときの見た目。AE2 ThingsのDISKと同じものを借りる。 */
    private static final ResourceLocation DRIVE_MODEL =
            ResourceLocation.fromNamespaceAndPath(MODID, "model/drive/cells/disk_256k");

    //? if >=1.21 {
    private final Map<InsaneCraftingUnitType, DeferredHolder<Item, Item>> disks =
    //?} else {
    /*private final Map<InsaneCraftingUnitType, RegistryObject<Item>> disks =
    *///?}
            new EnumMap<>(InsaneCraftingUnitType.class);

    @Override
    public String modId() {
        return MODID;
    }

    @Override
    public void registerContent() {
        for (InsaneCraftingUnitType tier : InsaneCraftingUnitType.values()) {
            ItemLike component = () -> ModItems.CELL_COMPONENTS.get(tier).get();
            disks.put(tier, ModCells.register("disk_drive_" + tier.id(),
                    () -> new InsaneDiskItem(component, tier.getStorageBytes(), ModCells.idleDrain(tier),
                            tier.label())));
        }
    }

    /** DISKのLEDはAE2 Thingsの色付けを使う。中身の量を保存データではなくアイテム側の値から読む。 */
    @Override
    public Map<Item, ToIntBiFunction<ItemStack, Integer>> cellsWithOwnColor() {
        Map<Item, ToIntBiFunction<ItemStack, Integer>> cells = new LinkedHashMap<>();
        disks.values().forEach(disk -> cells.put(disk.get(), DISKDrive::getColor));
        return cells;
    }

    /** AE2 Thingsが自分のDISKにしている登録と同じ。あいまいカード・反転カード。 */
    @Override
    public void registerUpgrades() {
        String group = "text.ae2things.disk_drives";
        //? if >=1.21 {
        for (DeferredHolder<Item, Item> disk : disks.values()) {
        //?} else {
        /*for (RegistryObject<Item> disk : disks.values()) {
        *///?}
            Upgrades.add(AEItems.FUZZY_CARD, disk.get(), 1, group);
            Upgrades.add(AEItems.INVERTER_CARD, disk.get(), 1, group);
            StorageCellModels.registerModel(disk.get(), DRIVE_MODEL);
        }
    }

    /** 作り方もAE2 Thingsと同じくDISKハウジング + セルコンポーネント。分解でもこの2つに戻る。 */
    @Override
    //? if >=1.21 {
    public void buildRecipes(RecipeOutput output, InsaneCraftingUnitType tier, ItemLike component) {
    //?} else {
    /*public void buildRecipes(Consumer<FinishedRecipe> output, InsaneCraftingUnitType tier, ItemLike component) {
    *///?}
        ModRecipeProvider.shapeless(output, disks.get(tier).get(), component,
                builder -> builder.requires(AETItems.DISK_HOUSING.get()).requires(component));
    }
}
