package jp.main.taikun.insaneae.integration.appflux;

import appeng.core.definitions.AEItems;
import appeng.core.localization.GuiText;
import appeng.api.upgrades.Upgrades;
import com.glodblock.github.appflux.api.IFluxCell;
import com.glodblock.github.appflux.common.AFSingletons;
import jp.main.taikun.insaneae.crafting.InsaneCraftingUnitType;
import jp.main.taikun.insaneae.datagen.ModRecipeProvider;
import jp.main.taikun.insaneae.integration.AddonIntegration;
import jp.main.taikun.insaneae.registries.ModCells;
import jp.main.taikun.insaneae.registries.ModItems;
import jp.main.taikun.insaneae.registries.ModUpgrades;
import jp.main.taikun.insaneae.util.TieredNames;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * AppliedFlux 連携: 1G〜16T の FE ストレージセル (通常 / ポータブル) を足す
 * (16T だけ容量 8T。理由は {@link FluxTiers})。
 *
 * <p>セルは AppliedFlux の {@link IFluxCell} 実装なので、ME ドライブ等に挿したときの中身は
 * AppliedFlux 自身のセルハンドラ ({@code FECellHandler}、{@code IFluxCell} なら誰のアイテムでも拾う)
 * が扱う。<b>Mixin は要らない</b>。容量が long を溢れる件は {@link FluxCapacity} を参照。</p>
 *
 * <p>このクラスは AppliedFlux のクラスを直接参照するので、appflux が無い環境では
 * ロードしてはいけない (名簿 {@code AddonIntegrations} がラムダ越しに生成している)。</p>
 */
public final class AppFluxIntegration implements AddonIntegration {

    public static final String MODID = "appflux";

    private final Map<InsaneCraftingUnitType, DeferredHolder<Item, Item>> cells =
            new EnumMap<>(InsaneCraftingUnitType.class);
    private final Map<InsaneCraftingUnitType, DeferredHolder<Item, Item>> portableCells =
            new EnumMap<>(InsaneCraftingUnitType.class);

    @Override
    public String modId() {
        return MODID;
    }

    @Override
    public void registerContent() {
        for (InsaneCraftingUnitType tier : FluxTiers.TIERS) {
            ItemLike component = () -> ModItems.CELL_COMPONENTS.get(tier).get();
            long bytes = FluxTiers.bytes(tier);
            cells.put(tier, ModCells.register("fe_storage_cell_" + tier.id(),
                    () -> new InsaneFECellItem(component, bytes, ModCells.idleDrain(tier),
                            TieredNames.FE_STORAGE_CELL, tier.label())));
            portableCells.put(tier, ModCells.register("portable_fe_cell_" + tier.id(),
                    () -> new InsanePortableFECellItem(bytes, ModCells.PORTABLE_SCREEN_COLOR,
                            TieredNames.PORTABLE_FE_CELL, tier.label())));
        }
    }

    @Override
    public List<Item> storageCells() {
        return cells.values().stream().map(DeferredHolder::get).toList();
    }

    @Override
    public List<Item> portableCells() {
        return portableCells.values().stream().map(DeferredHolder::get).toList();
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
            Upgrades.add(AFSingletons.INDUCTION_CARD, cell, 1, portableGroup);
        }
    }

    /**
     * FE の capability。AppliedFlux は自分のアイテムにしか登録しないので登録し直す。
     * 中身は AppliedFlux と同じく {@link IFluxCell#getCapability} に任せる
     * (通常セル = 貯めている FE、ポータブル = 内蔵電池)。
     */
    @Override
    public void registerCapabilities(RegisterCapabilitiesEvent event) {
        List<Item> all = new ArrayList<>(storageCells());
        all.addAll(portableCells());
        for (Item item : all) {
            // IFluxCell 自体が ICapabilityProvider<ItemStack, Void, IEnergyStorage>。
            event.registerItem(Capabilities.EnergyStorage.ITEM, (IFluxCell) item, item);
        }
    }

    @Override
    public void buildRecipes(RecipeOutput output, InsaneCraftingUnitType tier, ItemLike component) {
        if (!cells.containsKey(tier)) {
            return;  // FluxTiers.TOP より上の階層は出していない
        }
        ItemLike housing = AFSingletons.FE_HOUSING;
        ModRecipeProvider.storageCell(output, cells.get(tier).get(), component, housing);
        ModRecipeProvider.portableCell(output, portableCells.get(tier).get(), component, housing);
    }
}
