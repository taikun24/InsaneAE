package jp.main.taikun.insaneae.integration.appmek;

import appeng.api.stacks.AEKey;
import appeng.items.storage.StorageTier;
import gripe._90.megacells.definition.MEGAItems;
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
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.EnumMap;
import java.util.Map;

/**
 * Applied Mekanistics ({@code appmek}) が導入されている場合だけ追加される化学物質セル。
 *
 * <p>このクラスは appmek / Mekanism のクラスを直接参照するので、
 * <b>appmek が無い環境では絶対にロードしてはいけない</b>。
 * 呼び出し側 ({@code InsaneAE} のコンストラクタ) が {@code ModList} で分岐している。</p>
 *
 * <p>ハウジングは MEGA Cells の化学物質セルハウジング (これも appmek 導入時のみ登録される) を使う。
 * 放射性物質は appmek / MEGA Cells の通常セルと同じく弾く (専用セルの領分)。</p>
 */
public final class AppMekCells {

    public static final Map<InsaneCraftingUnitType, DeferredHolder<Item, Item>> CHEMICAL_CELLS =
            new EnumMap<>(InsaneCraftingUnitType.class);
    public static final Map<InsaneCraftingUnitType, DeferredHolder<Item, Item>> PORTABLE_CHEMICAL_CELLS =
            new EnumMap<>(InsaneCraftingUnitType.class);

    private AppMekCells() {
    }

    /** Mod 構築時に、appmek がロードされている場合のみ呼ぶ。 */
    public static void register() {
        for (InsaneCraftingUnitType tier : InsaneCraftingUnitType.values()) {
            double idleDrain = ModCells.idleDrain(tier);
            ItemLike component = () -> ModItems.CELL_COMPONENTS.get(tier).get();
            StorageTier storageTier = ModCells.TIERS.get(tier);

            CHEMICAL_CELLS.put(tier, ModCells.register("chemical_storage_cell_" + tier.id(),
                    () -> new InsaneStorageCellItem(new Item.Properties().stacksTo(1), component,
                            MEGAItems.MEGA_CHEMICAL_CELL_HOUSING, idleDrain, tier.getStorageBytes(),
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
                            AMMenus.PORTABLE_CHEMICAL_CELL_TYPE, ModCells.PORTABLE_SCREEN_COLOR,
                            TieredNames.PORTABLE_CHEMICAL_CELL, tier.label()) {
                        @Override
                        public boolean isBlackListed(ItemStack cellItem, AEKey requestedAddition) {
                            return isRadioactive(requestedAddition)
                                    || super.isBlackListed(cellItem, requestedAddition);
                        }
                    }));
        }
    }

    /** クリエイティブタブへの追加 (appmek 導入時のみ呼ばれる)。 */
    public static void addToCreativeTab(java.util.function.Consumer<Item> output) {
        CHEMICAL_CELLS.values().forEach(cell -> output.accept(cell.get()));
        PORTABLE_CHEMICAL_CELLS.values().forEach(cell -> output.accept(cell.get()));
    }

    /** 放射性の化学物質かどうか (appmek の通常セルと同じ判定)。 */
    private static boolean isRadioactive(AEKey key) {
        return key instanceof MekanismKey mekanismKey
                && !ChemicalAttributeValidator.DEFAULT.process(mekanismKey.getStack());
    }
}
