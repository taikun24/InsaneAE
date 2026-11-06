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
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToIntBiFunction;

/**
 * AE2 Things 連携: 1G〜8E のアイテム用 DISK を足す (AE2 Things の 256k、AE2 MEGA Things の 256M の続き)。
 *
 * <p>DISK は種類の上限が無い代わりに、通常のセルの 1/8 (アイテム 1 個 = 1 バイト) しか入らない。
 * 中身は AE2 Things が UUID ごとにワールドへ保存する。液体・化学物質の DISK は AE2 Things 本体に
 * 無い (絵も無い) ので足していない。</p>
 *
 * <p>このクラスは AE2 Things のクラスを直接参照するので、ae2things が無い環境では
 * ロードしてはいけない (名簿 {@code AddonIntegrations} がラムダ越しに生成している)。</p>
 */
public final class AE2ThingsIntegration implements AddonIntegration {

    public static final String MODID = "ae2things";

    /** ME ドライブに挿したときの見た目。AE2 Things の DISK と同じものを借りる。 */
    private static final ResourceLocation DRIVE_MODEL =
            ResourceLocation.fromNamespaceAndPath(MODID, "model/drive/cells/disk_256k");

    private final Map<InsaneCraftingUnitType, DeferredHolder<Item, Item>> disks =
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

    /** DISK の LED は AE2 Things の色付けを使う (中身の量を保存データではなくアイテム側の値から読む)。 */
    @Override
    public Map<Item, ToIntBiFunction<ItemStack, Integer>> cellsWithOwnColor() {
        Map<Item, ToIntBiFunction<ItemStack, Integer>> cells = new LinkedHashMap<>();
        disks.values().forEach(disk -> cells.put(disk.get(), DISKDrive::getColor));
        return cells;
    }

    /** AE2 Things が自分の DISK にしている登録と同じ (あいまいカード・反転カード)。 */
    @Override
    public void registerUpgrades() {
        String group = "text.ae2things.disk_drives";
        for (DeferredHolder<Item, Item> disk : disks.values()) {
            Upgrades.add(AEItems.FUZZY_CARD, disk.get(), 1, group);
            Upgrades.add(AEItems.INVERTER_CARD, disk.get(), 1, group);
            StorageCellModels.registerModel(disk.get(), DRIVE_MODEL);
        }
    }

    /** 作り方も AE2 Things と同じく DISK ハウジング + セルコンポーネント。分解でもこの 2 つに戻る。 */
    @Override
    public void buildRecipes(RecipeOutput output, InsaneCraftingUnitType tier, ItemLike component) {
        ModRecipeProvider.shapeless(output, disks.get(tier).get(), component,
                builder -> builder.requires(AETItems.DISK_HOUSING.get()).requires(component));
    }
}
