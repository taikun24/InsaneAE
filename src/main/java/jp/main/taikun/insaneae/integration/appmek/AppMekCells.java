package jp.main.taikun.insaneae.integration.appmek;

import appeng.api.stacks.AEKey;
import appeng.items.storage.StorageTier;
import jp.main.taikun.insaneae.cell.InsanePortableCellItem;
import jp.main.taikun.insaneae.cell.InsaneStorageCellItem;
import jp.main.taikun.insaneae.crafting.InsaneCraftingUnitType;
import jp.main.taikun.insaneae.registries.ModCells;
import jp.main.taikun.insaneae.registries.ModItems;
import jp.main.taikun.insaneae.util.TieredNames;
import me.ramidzkh.mekae2.AMMenus;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import me.ramidzkh.mekae2.ae2.MekanismKeyType;
import mekanism.api.chemical.attribute.ChemicalAttributeValidator;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.util.EnumMap;
import java.util.Map;
//? if >=1.21 {
import gripe._90.megacells.definition.MEGAItems;
import net.neoforged.neoforge.registries.DeferredHolder;
//?}
//? if <1.21 {
/*import gripe._90.megacells.integration.appmek.AppMekItems;
import net.neoforged.neoforge.registries.RegistryObject;
*///?}

/**
 * Applied Mekanistics ({@code appmek}) が導入されている場合だけ追加される化学物質セル。
 *
 * <p>このクラスはappmek / Mekanismのクラスを直接参照するので、
 * appmekが無い環境では絶対にロードしてはいけない。
 * 呼び出し側 ({@link AppMekIntegration}) はappmekが入っているときしか作られない。</p>
 *
 * <p>ハウジングはMEGA Cellsの化学物質セルハウジングを使う。これもappmek導入時のみ登録される。
 * 放射性物質はappmek / MEGA Cellsの通常セルと同じく弾く。専用セルの領分だから。</p>
 */
public final class AppMekCells {

    //? if >=1.21 {
    public static final Map<InsaneCraftingUnitType, DeferredHolder<Item, Item>> CHEMICAL_CELLS =
    //?} else {
    /*public static final Map<InsaneCraftingUnitType, RegistryObject<Item>> CHEMICAL_CELLS =
    *///?}
            new EnumMap<>(InsaneCraftingUnitType.class);
    //? if >=1.21 {
    public static final Map<InsaneCraftingUnitType, DeferredHolder<Item, Item>> PORTABLE_CHEMICAL_CELLS =
    //?} else {
    /*public static final Map<InsaneCraftingUnitType, RegistryObject<Item>> PORTABLE_CHEMICAL_CELLS =
    *///?}
            new EnumMap<>(InsaneCraftingUnitType.class);

    private AppMekCells() {
    }

    /** Mod構築時に、appmekがロードされている場合のみ呼ぶ ({@link AppMekIntegration#registerContent})。 */
    public static void register() {
        for (InsaneCraftingUnitType tier : InsaneCraftingUnitType.values()) {
            double idleDrain = ModCells.idleDrain(tier);
            ItemLike component = () -> ModItems.CELL_COMPONENTS.get(tier).get();
            StorageTier storageTier = ModCells.TIERS.get(tier);

            CHEMICAL_CELLS.put(tier, ModCells.register("chemical_storage_cell_" + tier.id(),
                    () -> new InsaneStorageCellItem(new Item.Properties().stacksTo(1), component,
                            //? if >=1.21 {
                            MEGAItems.MEGA_CHEMICAL_CELL_HOUSING, idleDrain, tier.getStorageBytes(),
                            //?} else {
                            /*AppMekItems.MEGA_CHEMICAL_CELL_HOUSING, idleDrain, tier.getStorageBytes(),
                            *///?}
                            ModCells.BYTES_PER_TYPE, ModCells.totalTypes(tier), MekanismKeyType.TYPE,
                            TieredNames.CHEMICAL_STORAGE_CELL, tier.label()) {
                        @Override
                        public boolean isBlackListed(ItemStack cellItem, AEKey requestedAddition) {
                            return isRadioactive(requestedAddition)
                                    || super.isBlackListed(cellItem, requestedAddition);
                        }
                    }));

            PORTABLE_CHEMICAL_CELLS.put(tier, ModCells.register("portable_chemical_cell_" + tier.id(),
                    () -> new InsanePortableCellItem(new Item.Properties(), storageTier,
                            tier.getStorageBytes(), ModCells.totalTypes(tier), MekanismKeyType.TYPE,
                            //? if >=1.21 {
                            AMMenus.PORTABLE_CHEMICAL_CELL_TYPE, ModCells.PORTABLE_SCREEN_COLOR,
                            //?} else {
                            /*AMMenus.PORTABLE_CHEMICAL_CELL_TYPE, 0x33528D,
                            *///?}
                            TieredNames.PORTABLE_CHEMICAL_CELL, tier.label()) {
                        @Override
                        public boolean isBlackListed(ItemStack cellItem, AEKey requestedAddition) {
                            return isRadioactive(requestedAddition)
                                    || super.isBlackListed(cellItem, requestedAddition);
                        }
                    }));
        }
    }

    /** 放射性の化学物質かどうか。appmekの通常セルと同じ判定。 */
    private static boolean isRadioactive(AEKey key) {
        return key instanceof MekanismKey mekanismKey
                && !ChemicalAttributeValidator.DEFAULT.process(mekanismKey.getStack());
    }
}
