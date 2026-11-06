package jp.main.taikun.insaneae.integration.appflux;

import appeng.core.definitions.AEItems;
import appeng.core.localization.GuiText;
import appeng.api.upgrades.Upgrades;
import com.glodblock.github.appflux.api.IFluxCell;
import jp.main.taikun.insaneae.crafting.InsaneCraftingUnitType;
import jp.main.taikun.insaneae.datagen.ModRecipeProvider;
import jp.main.taikun.insaneae.integration.AddonIntegration;
import jp.main.taikun.insaneae.registries.ModCells;
import jp.main.taikun.insaneae.registries.ModUpgrades;
import jp.main.taikun.insaneae.util.TieredItem;
import jp.main.taikun.insaneae.util.TieredNames;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
//? if >=1.21 {
import com.glodblock.github.appflux.common.AFSingletons;
import net.minecraft.data.recipes.RecipeOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import java.util.ArrayList;
//?}
//? if <1.21 {
/*import com.glodblock.github.appflux.common.AFItemAndBlock;
import com.glodblock.github.appflux.util.helpers.INeighborListener;
import net.minecraft.core.Direction;
import net.minecraft.data.recipes.FinishedRecipe;
import net.neoforged.neoforge.registries.RegistryObject;
import java.util.function.Consumer;
*///?}

/**
 * AppliedFlux 連携: 1G〜16T の FE ストレージセル (通常 / ポータブル) を足す
 * (16T だけ容量 8T。理由は {@link FluxTiers})。材料は AppliedFlux と同じく専用の
 * エネルギーストレージコンポーネントで、これも 1G〜16T を足す (AppliedFlux の 256M の続き)。
 *
 * <p>セルは AppliedFlux の {@link IFluxCell} 実装なので、ME ドライブ等に挿したときの中身は
 * AppliedFlux 自身のセルハンドラ ({@code FECellHandler}、{@code IFluxCell} なら誰のアイテムでも対象にする)
 * が扱う。<b>Mixin は要らない</b>。容量が long を溢れる件は {@link FluxCapacity} を参照。</p>
 *
 * <p>このクラスは AppliedFlux のクラスを直接参照するので、appflux が無い環境では
 * ロードしてはいけない (名簿 {@code AddonIntegrations} がラムダ越しに生成している)。</p>
 */
public final class AppFluxIntegration implements AddonIntegration {

    public static final String MODID = "appflux";

    /** 各階層のエネルギーストレージコンポーネント (FE セルの材料。AppliedFlux の 1k〜256M の続き)。 */
    //? if >=1.21 {
    private final Map<InsaneCraftingUnitType, DeferredHolder<Item, Item>> components =
    //?} else {
    /*private static final int PORTABLE_SCREEN_COLOR = 0xDDDDDD;

    /^* 各階層のエネルギーストレージコンポーネント (FE セルの材料。AppliedFlux の 1k〜256M の続き)。 ^/
    private final Map<InsaneCraftingUnitType, RegistryObject<Item>> components =
    *///?}
            new EnumMap<>(InsaneCraftingUnitType.class);
    //? if >=1.21 {
    private final Map<InsaneCraftingUnitType, DeferredHolder<Item, Item>> cells =
    //?} else {
    /*private final Map<InsaneCraftingUnitType, RegistryObject<Item>> cells =
    *///?}
            new EnumMap<>(InsaneCraftingUnitType.class);
    //? if >=1.21 {
    private final Map<InsaneCraftingUnitType, DeferredHolder<Item, Item>> portableCells =
    //?} else {
    /*private final Map<InsaneCraftingUnitType, RegistryObject<Item>> portableCells =
    *///?}
            new EnumMap<>(InsaneCraftingUnitType.class);

    @Override
    public String modId() {
        return MODID;
    }

    @Override
    public void registerContent() {
        for (InsaneCraftingUnitType tier : FluxTiers.TIERS) {
            //? if >=1.21 {
            DeferredHolder<Item, Item> core = ModCells.register("energy_component_" + tier.id(),
            //?} else {
            /*RegistryObject<Item> core = ModCells.register("energy_component_" + tier.id(),
            *///?}
                    () -> new TieredItem(new Item.Properties(), TieredNames.ENERGY_COMPONENT, tier.label()));
            components.put(tier, core);
            ItemLike component = core::get;
            long bytes = FluxTiers.bytes(tier);
            cells.put(tier, ModCells.register("fe_storage_cell_" + tier.id(),
                    () -> new InsaneFECellItem(component, bytes, ModCells.idleDrain(tier),
                            TieredNames.FE_STORAGE_CELL, tier.label())));
            portableCells.put(tier, ModCells.register("portable_fe_cell_" + tier.id(),
                    //? if >=1.21 {
                    () -> new InsanePortableFECellItem(bytes, ModCells.PORTABLE_SCREEN_COLOR,
                    //?} else {
                    /*() -> new InsanePortableFECellItem(bytes, PORTABLE_SCREEN_COLOR,
                    *///?}
                            TieredNames.PORTABLE_FE_CELL, tier.label())));
        }
    }

    @Override
    public List<Item> materials() {
        //? if >=1.21 {
        return components.values().stream().map(DeferredHolder::get).toList();
        //?} else {
        /*return components.values().stream().map(RegistryObject::get).toList();
        *///?}
    }

    @Override
    public List<Item> storageCells() {
        //? if >=1.21 {
        return cells.values().stream().map(DeferredHolder::get).toList();
        //?} else {
        /*return cells.values().stream().map(RegistryObject::get).toList();
        *///?}
    }

    @Override
    public List<Item> portableCells() {
        //? if >=1.21 {
        return portableCells.values().stream().map(DeferredHolder::get).toList();
        //?} else {
        /*return portableCells.values().stream().map(RegistryObject::get).toList();
        *///?}
    }

    /**
     * AppliedFlux が自分の FE セルにしている登録と同じ: 通常 = 超過破棄カード、
     * ポータブル = 超過破棄 + エネルギーカード ×2 + 誘導カード (持ち物の FE 機器へ給電)。
     * FE は 1 種類しか無いので、あいまい/白黒/均等配分は意味が無く登録しない。
     */
    @Override
    public void registerUpgrades() {
        String cellGroup = GuiText.StorageCells.getTranslationKey();
        String portableGroup = GuiText.PortableCells.getTranslationKey();
        for (Item cell : storageCells()) {
            Upgrades.add(AEItems.VOID_CARD, cell, 1, cellGroup);
        }
        for (Item cell : portableCells()) {
            Upgrades.add(AEItems.VOID_CARD, cell, 1, portableGroup);
            ModUpgrades.addPortableEnergyCards(cell, portableGroup);
            //? if >=1.21 {
            Upgrades.add(AFSingletons.INDUCTION_CARD, cell, 1, portableGroup);
            //?} else {
            /*Upgrades.add(AFItemAndBlock.INDUCTION_CARD, cell, 1, portableGroup);
            *///?}
        }
    }

    /**
     * FE の capability。AppliedFlux は自分のアイテムにしか登録しないので登録し直す。
     * 中身は AppliedFlux と同じく {@link IFluxCell#getCapability} に任せる
     * (通常セル = 貯めている FE、ポータブル = 内蔵電池)。
     */
    @Override
    //? if >=1.21 {
    public void registerCapabilities(RegisterCapabilitiesEvent event) {
        List<Item> all = new ArrayList<>(storageCells());
        all.addAll(portableCells());
        for (Item item : all) {
            // IFluxCell 自体が ICapabilityProvider<ItemStack, Void, IEnergyStorage>。
            event.registerItem(Capabilities.EnergyStorage.ITEM, (IFluxCell) item, item);
    //?} else {
    /*public void onInsaneHostNeighborChanged(Object logic, Direction side) {
        if (logic instanceof INeighborListener listener) {
            listener.onChange(side);
    *///?}
        }
    }

    @Override
    //? if >=1.21 {
    public void buildRecipes(RecipeOutput output, InsaneCraftingUnitType tier, ItemLike cellComponent) {
    //?} else {
    /*public void buildRecipes(Consumer<FinishedRecipe> output, InsaneCraftingUnitType tier, ItemLike cellComponent) {
    *///?}
        if (!cells.containsKey(tier)) {
            return;  // FluxTiers.TOP より上の階層は出していない
        }
        // エネルギーコンポーネント: 通常のセルコンポーネントと同じ形で、プロセッサが
        // AppliedFlux のエネルギープロセッサ。最下段 (1G) は AppliedFlux の 256M から作る。
        ItemLike core = components.get(tier).get();
        ItemLike lower = tier.ordinal() == 0 ?
                //? if >=1.21 {
                AFSingletons.CORE_256M
                //?} else {
                /*AFItemAndBlock.CORE_256M
                *///?}
                : components.get(InsaneCraftingUnitType.values()[tier.ordinal() - 1]).get();
        //? if >=1.21 {
        ModRecipeProvider.tieredComponent(output, core, lower, AFSingletons.ENERGY_PROCESSOR, tier);

        // FE セル: AppliedFlux と同じく FE セル筐体 + エネルギーコンポーネント (分解でもこれに戻る)。
        // 引数の cellComponent (通常のセルコンポーネント) は使わない。
        ItemLike housing = AFSingletons.FE_HOUSING;
        //?} else {
        /*ModRecipeProvider.tieredComponent(output, core, lower, AFItemAndBlock.ENERGY_PROCESSOR, tier);

        // FE セル: MEGA の FE セル筐体 (AppliedFlux の 1M〜256M と同じ) + エネルギーコンポーネント。
        // 分解でもこの 2 つに戻る。引数の cellComponent (通常のセルコンポーネント) は使わない。
        ItemLike housing = AFItemAndBlock.MEGA_FE_HOUSING;
        *///?}
        ModRecipeProvider.storageCell(output, cells.get(tier).get(), core, housing);
        ModRecipeProvider.portableCell(output, portableCells.get(tier).get(), core, housing);
    }
}
