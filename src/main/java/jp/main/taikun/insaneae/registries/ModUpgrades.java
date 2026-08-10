package jp.main.taikun.insaneae.registries;

import net.minecraft.core.registries.Registries;
import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.core.localization.GuiText;
import jp.main.taikun.insaneae.InsaneAE;
import jp.main.taikun.insaneae.quantum.QuantumCpuBlockEntity;
import jp.main.taikun.insaneae.upgrade.InsaneSpeedCardItem;
import jp.main.taikun.insaneae.upgrade.InsaneSpeedCardType;
import jp.main.taikun.insaneae.upgrade.QuantumAccelerationCardItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 加速カード (アップグレードカード) の登録。
 *
 * <p>取り付け可能な機械は AE2 の加速カードと同じ顔ぶれのうち、
 * 速度倍率を実装済みのものに限っている ({@link #SUPPORTED_MACHINES})。</p>
 */
public class ModUpgrades {
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, InsaneAE.MODID);

    /** 1 機械あたりの取り付け上限。倍率が大きいので 1 枚で十分。 */
    private static final int MAX_INSTALLED = 1;

    /** 速度倍率を実装済みの機械。 */
    private static final List<ItemLike> SUPPORTED_MACHINES = List.of(
            AEParts.IMPORT_BUS,
            AEParts.EXPORT_BUS,
            AEBlocks.MOLECULAR_ASSEMBLER,
            AEBlocks.INSCRIBER,
            AEBlocks.IO_PORT);

    public static final Map<InsaneSpeedCardType, DeferredHolder<Item, Item>> SPEED_CARDS =
            new EnumMap<>(InsaneSpeedCardType.class);

    /** Quantum CPU 専用の加速カード。1 枚ごとに組み立て速度が 256 倍。 */
    public static final DeferredHolder<Item, Item> QUANTUM_ACCELERATION_CARD =
            ITEMS.register("quantum_acceleration_card",
                    () -> new QuantumAccelerationCardItem(new Item.Properties()));

    static {
        for (InsaneSpeedCardType type : InsaneSpeedCardType.values()) {
            DeferredHolder<Item, Item> card = ITEMS.register(type.id(),
                    () -> new InsaneSpeedCardItem(new Item.Properties(), type));
            type.setItem(card::get);
            SPEED_CARDS.put(type, card);
        }
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }

    /** AE2 のアップグレード登録。アイテムが揃った後 (commonSetup) に呼ぶこと。 */
    public static void registerUpgrades() {
        for (InsaneSpeedCardType type : InsaneSpeedCardType.values()) {
            for (ItemLike machine : SUPPORTED_MACHINES) {
                Upgrades.add(type.item(), machine, MAX_INSTALLED);
            }
        }
        Upgrades.add(QUANTUM_ACCELERATION_CARD.get(), ModBlocks.QUANTUM_CPU.get(),
                QuantumCpuBlockEntity.MAX_ACCELERATION_CARDS);

        registerCellUpgrades();
        registerCompatMachines();
    }

    /**
     * 自作の ME ストレージセルにアップグレードカードを挿せるようにする。
     *
     * <p>セルの中身 ({@code BasicCellInventory}) はカードの有無を自分で見るので、
     * <b>登録さえすれば挙動は AE2 のまま動く</b>。逆に登録しないと、セルワークベンチが
     * どのカードも受け付けない (「追加されたセルに拡張カードを挿せない」不具合の原因)。
     * 登録内容は AE2 が自分のセルにしているもの ({@code InitUpgrades}) と同じ:
     * アイテム系はあいまい/白黒/均等配分/超過破棄、液体・化学物質系はあいまい以外、
     * ポータブルは加えてエネルギーカード ×2。</p>
     *
     * <p>ツールチップの行 (第 4 引数) も AE2 と同じグループ名にまとめる。
     * まとめないと、カード側のツールチップに<b>セル 1 種類につき 1 行</b>
     * (階層 × 種別ぶん) がずらずら並ぶ。</p>
     */
    private static void registerCellUpgrades() {
        String cells = GuiText.StorageCells.getTranslationKey();
        String portables = GuiText.PortableCells.getTranslationKey();

        for (DeferredHolder<Item, Item> cell : ModCells.ITEM_CELLS.values()) {
            addItemCellCards(cell.get(), cells);
        }
        for (DeferredHolder<Item, Item> cell : ModCells.FLUID_CELLS.values()) {
            addFluidCellCards(cell.get(), cells);
        }
        for (DeferredHolder<Item, Item> cell : ModCells.PORTABLE_ITEM_CELLS.values()) {
            addItemCellCards(cell.get(), portables);
            Upgrades.add(AEItems.ENERGY_CARD, cell.get(), 2, portables);
        }
        for (DeferredHolder<Item, Item> cell : ModCells.PORTABLE_FLUID_CELLS.values()) {
            addFluidCellCards(cell.get(), portables);
            Upgrades.add(AEItems.ENERGY_CARD, cell.get(), 2, portables);
        }
        // 化学物質セル (Applied Mekanistics 導入時のみ)。appmek が自分のセルにしている登録と同じ。
        if (net.neoforged.fml.ModList.get().isLoaded(InsaneAE.APPMEK_MODID)) {
            for (DeferredHolder<Item, Item> cell :
                    jp.main.taikun.insaneae.integration.appmek.AppMekCells.CHEMICAL_CELLS.values()) {
                addFluidCellCards(cell.get(), cells);
            }
            for (DeferredHolder<Item, Item> cell :
                    jp.main.taikun.insaneae.integration.appmek.AppMekCells.PORTABLE_CHEMICAL_CELLS.values()) {
                addFluidCellCards(cell.get(), portables);
                Upgrades.add(AEItems.ENERGY_CARD, cell.get(), 2, portables);
            }
        }
        // 強化クリエイティブセルは AE2 のクリエイティブセルと同じくカード無し。
    }

    /** アイテムを入れるセルが受けるカード。 */
    private static void addItemCellCards(ItemLike cell, String tooltipGroup) {
        Upgrades.add(AEItems.FUZZY_CARD, cell, 1, tooltipGroup);
        addFluidCellCards(cell, tooltipGroup);
    }

    /** 液体・化学物質のセルが受けるカード (あいまいカードはスタック NBT の概念が無いので除く)。 */
    private static void addFluidCellCards(ItemLike cell, String tooltipGroup) {
        Upgrades.add(AEItems.INVERTER_CARD, cell, 1, tooltipGroup);
        Upgrades.add(AEItems.EQUAL_DISTRIBUTION_CARD, cell, 1, tooltipGroup);
        Upgrades.add(AEItems.VOID_CARD, cell, 1, tooltipGroup);
    }

    /**
     * 他 Mod の「AE2 の加速カードが挿せる機械」にもこちらの加速カードを挿せるようにする。
     *
     * <p>挿せるだけでは意味が無いので、対象は<b>速度倍率の Mixin を用意した機械だけ</b>
     * (mixin/compat の Ex〜・AAE〜 を参照。バス系は AE2 の基底クラスの Mixin がそのまま効く)。
     * どの Mod もコンパイル依存には入れず、登録名からアイテムを引く。
     * アイテムが見つからない場合 (相手の改名など) は静かに飛ばす —
     * カードが挿せないだけで、壊れはしない。</p>
     */
    private static void registerCompatMachines() {
        // ExtendedAE (1.21 の modid は extendedae。1.20.1 は expatternprovider だった)。
        registerCompatMachines("extendedae", List.of(
                "ex_import_bus_part",       // 基底 IOBusPart の Mixin が効く
                "ex_export_bus_part",
                "tag_export_bus",
                "mod_export_bus",
                "precise_export_bus",
                "threshold_export_bus",
                "active_formation_plane",   // ExFormationPlaneMixin
                "ex_molecular_assembler",   // ExCraftingThreadMixin
                "ex_inscriber",             // ExInscriberThreadMixin
                "ex_io_port",               // ExIOPortMixin
                "circuit_cutter",           // ExCircuitCutterMixin
                "crystal_assembler"));      // ExCrystalAssemblerMixin (1.21 で追加された機械)
        // Advanced AE。バス 3 種は AE2 の ExportBusPart/IOBusPart 経由で効く。
        registerCompatMachines("advanced_ae", List.of(
                "stock_export_bus_part",
                "import_export_bus_part",
                "advanced_io_bus_part",
                "quantum_crafter",          // AAEQuantumCrafterMixin
                "reaction_chamber"));       // AAEReactionChamberMixin
    }

    private static void registerCompatMachines(String modId, List<String> itemIds) {
        if (!net.neoforged.fml.ModList.get().isLoaded(modId)) {
            return;
        }
        for (String itemId : itemIds) {
            Item machine = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(modId, itemId));
            if (machine != Items.AIR) {
                for (InsaneSpeedCardType type : InsaneSpeedCardType.values()) {
                    Upgrades.add(type.item(), machine, MAX_INSTALLED,
                            "insaneae.upgrade_group." + modId);
                }
            }
        }
    }
}
