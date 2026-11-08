package jp.main.taikun.insaneae.registries;

import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.core.localization.GuiText;
import gripe._90.megacells.definition.MEGAItems;
import jp.main.taikun.insaneae.InsaneAE;
import jp.main.taikun.insaneae.quantum.QuantumCpuBlockEntity;
import jp.main.taikun.insaneae.upgrade.InsaneSpeedCardItem;
import jp.main.taikun.insaneae.upgrade.InsaneSpeedCardType;
import jp.main.taikun.insaneae.upgrade.QuantumAccelerationCardItem;
import jp.main.taikun.insaneae.upgrade.TaskFusionCardItem;
import jp.main.taikun.insaneae.upgrade.UpgradeMirrors;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import jp.main.taikun.insaneae.integration.AddonIntegration;
import jp.main.taikun.insaneae.integration.AddonIntegrations;
//? if >=1.21 {
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;
//?}
//? if <1.21 {
/*import net.neoforged.neoforge.registries.ForgeRegistries;
import net.neoforged.neoforge.registries.RegistryObject;
*///?}

/**
 * 加速カード、つまりアップグレードカードの登録。
 *
 * <p>取り付けられる機械は、AE2の加速カードを挿せる機械のうち
 * 速度倍率を実装済みのものに限っている ({@link #SUPPORTED_MACHINES})。</p>
 */
public class ModUpgrades {
    private static final DeferredRegister<Item> ITEMS =
            //? if >=1.21 {
            DeferredRegister.create(Registries.ITEM, InsaneAE.MODID);
            //?} else {
            /*DeferredRegister.create(ForgeRegistries.ITEMS, InsaneAE.MODID);
            *///?}

    /** 1機械あたりの取り付け上限。倍率が大きいので1枚で十分。 */
    private static final int MAX_INSTALLED = 1;

    /** 速度倍率を実装済みの機械。 */
    private static final List<ItemLike> SUPPORTED_MACHINES = List.of(
            AEParts.IMPORT_BUS,
            AEParts.EXPORT_BUS,
            AEBlocks.MOLECULAR_ASSEMBLER,
            AEBlocks.INSCRIBER,
            AEBlocks.IO_PORT);

    //? if >=1.21 {
    public static final Map<InsaneSpeedCardType, DeferredHolder<Item, Item>> SPEED_CARDS =
    //?} else {
    /*public static final Map<InsaneSpeedCardType, RegistryObject<Item>> SPEED_CARDS =
    *///?}
            new EnumMap<>(InsaneSpeedCardType.class);

    /** Quantum CPU専用の加速カード。1枚ごとに組み立て速度が256倍。 */
    //? if >=1.21 {
    public static final DeferredHolder<Item, Item> QUANTUM_ACCELERATION_CARD =
    //?} else {
    /*public static final RegistryObject<Item> QUANTUM_ACCELERATION_CARD =
    *///?}
            ITEMS.register("quantum_acceleration_card",
                    () -> new QuantumAccelerationCardItem(new Item.Properties()));

    /** Quantum CPU専用のタスク統合カード。まとめ1回をクラスタ予算の1操作として数えさせる。 */
    //? if >=1.21 {
    public static final DeferredHolder<Item, Item> TASK_FUSION_CARD =
    //?} else {
    /*public static final RegistryObject<Item> TASK_FUSION_CARD =
    *///?}
            ITEMS.register("task_fusion_card",
                    () -> new TaskFusionCardItem(new Item.Properties()));

    static {
        for (InsaneSpeedCardType type : InsaneSpeedCardType.values()) {
            //? if >=1.21 {
            DeferredHolder<Item, Item> card = ITEMS.register(type.id(),
            //?} else {
            /*RegistryObject<Item> card = ITEMS.register(type.id(),
            *///?}
                    () -> new InsaneSpeedCardItem(new Item.Properties(), type));
            type.setItem(card::get);
            SPEED_CARDS.put(type, card);
        }
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }

    /** AE2のアップグレード登録。アイテムが揃った後 (commonSetup) に呼ぶこと。 */
    public static void registerUpgrades() {
        for (InsaneSpeedCardType type : InsaneSpeedCardType.values()) {
            for (ItemLike machine : SUPPORTED_MACHINES) {
                Upgrades.add(type.item(), machine, MAX_INSTALLED);
            }
        }
        Upgrades.add(QUANTUM_ACCELERATION_CARD.get(), ModBlocks.QUANTUM_CPU.get(),
                QuantumCpuBlockEntity.MAX_ACCELERATION_CARDS);
        Upgrades.add(TASK_FUSION_CARD.get(), ModBlocks.QUANTUM_CPU.get(), 1);

        registerCellUpgrades();
        AddonIntegrations.active().forEach(AddonIntegration::registerUpgrades);

        // AE2のインターフェイス / パターンプロバイダに登録されたカード。AE2自身のものも、
        // 他アドオンのものも) を超次元版にも登録する。ここより後の登録はその場で写る
        UpgradeMirrors.enable();
    }
    //? if <1.21 {
    /*public static void allowSpeedCards(String modId, List<String> itemIds) {
        for (String itemId : itemIds) {
            Item machine = ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath(modId, itemId));
            if (machine != null && machine != Items.AIR) {
                for (InsaneSpeedCardType type : InsaneSpeedCardType.values()) {
                    Upgrades.add(type.item(), machine, MAX_INSTALLED,
                            "insaneae.upgrade_group." + modId);
                }
            }
        }
    }
    *///?}

    /**
     * 自作のMEストレージセルにアップグレードカードを挿せるようにする。
     *
     * <p>セルの中身 ({@code BasicCellInventory}) はカードの有無を自分で見るので、
     * 登録さえすれば挙動はAE2のまま動く。逆に登録しないと、セルワークベンチが
     * どのカードも受け付けない。「追加されたセルに拡張カードを挿せない」不具合の原因。
     * 登録内容はAE2が自分のセルにしているもの ({@code InitUpgrades}) と同じ:
     * アイテム系はあいまい/白黒/均等配分/超過破棄、液体・化学物質系はあいまい以外、
     * ポータブルは加えてエネルギーカード ×2。</p>
     *
     * <p>ツールチップの行、つまり第4引数もAE2と同じグループ名にまとめる。
     * まとめないと、カード側のツールチップにセル1種類につき1行
     * 階層と種別ぶんが並んでしまう。</p>
     */
    private static void registerCellUpgrades() {
        String cells = GuiText.StorageCells.getTranslationKey();
        String portables = GuiText.PortableCells.getTranslationKey();

        //? if >=1.21 {
        for (DeferredHolder<Item, Item> cell : ModCells.ITEM_CELLS.values()) {
        //?} else {
        /*for (RegistryObject<Item> cell : ModCells.ITEM_CELLS.values()) {
        *///?}
            addItemCellCards(cell.get(), cells);
        }
        //? if >=1.21 {
        for (DeferredHolder<Item, Item> cell : ModCells.FLUID_CELLS.values()) {
        //?} else {
        /*for (RegistryObject<Item> cell : ModCells.FLUID_CELLS.values()) {
        *///?}
            addFluidCellCards(cell.get(), cells);
        }
        //? if >=1.21 {
        for (DeferredHolder<Item, Item> cell : ModCells.PORTABLE_ITEM_CELLS.values()) {
        //?} else {
        /*for (RegistryObject<Item> cell : ModCells.PORTABLE_ITEM_CELLS.values()) {
        *///?}
            addItemCellCards(cell.get(), portables);
            addPortableEnergyCards(cell.get(), portables);
        }
        //? if >=1.21 {
        for (DeferredHolder<Item, Item> cell : ModCells.PORTABLE_FLUID_CELLS.values()) {
        //?} else {
        /*for (RegistryObject<Item> cell : ModCells.PORTABLE_FLUID_CELLS.values()) {
        *///?}
            addFluidCellCards(cell.get(), portables);
            addPortableEnergyCards(cell.get(), portables);
        }
        // 強化クリエイティブセルはAE2のクリエイティブセルと同じくカード無し
    }

    /**
     * ポータブルセルの電力カード。AE2のエネルギーカードに加えて
     * MEGA CellsのGreater Energy Cardも受ける。
     *
     * <p>枚数 (2) もツールチップのまとめ先もMEGAが自分のポータブルセルにしている登録と同じ。
     * カードの効果はAE2の {@code PortableCellItem} 側が処理するので、
     * 挿せるようにするだけで容量が増える。MEGA Cellsは必須依存なので分岐は要らない。</p>
     */
    public static void addPortableEnergyCards(ItemLike cell, String tooltipGroup) {
        Upgrades.add(AEItems.ENERGY_CARD, cell, 2, tooltipGroup);
        Upgrades.add(MEGAItems.GREATER_ENERGY_CARD, cell, 2, tooltipGroup);
    }

    /** アイテムを入れるセルが受けるカード。 */
    public static void addItemCellCards(ItemLike cell, String tooltipGroup) {
        Upgrades.add(AEItems.FUZZY_CARD, cell, 1, tooltipGroup);
        addFluidCellCards(cell, tooltipGroup);
    }

    /** 液体・化学物質のセルが受けるカード。あいまいカードはスタックNBTの概念が無いので除く。 */
    public static void addFluidCellCards(ItemLike cell, String tooltipGroup) {
        Upgrades.add(AEItems.INVERTER_CARD, cell, 1, tooltipGroup);
        Upgrades.add(AEItems.EQUAL_DISTRIBUTION_CARD, cell, 1, tooltipGroup);
        Upgrades.add(AEItems.VOID_CARD, cell, 1, tooltipGroup);
    }

    /**
     * 他Modの「AE2の加速カードが挿せる機械」にもこちらの加速カードを挿せるようにする。
     * 各アドオン連携 ({@code integration/}) の {@code registerUpgrades} から呼ぶ。
     *
     * <p>挿せるだけでは意味が無いので、対象は速度倍率のMixinを用意した機械だけ
     * mixin/compatのEx〜とAAE〜を参照。バス系はAE2の基底クラスのMixinがそのまま効く。
     * どのModもコンパイル依存には入れず、登録名からアイテムを引く。
     * アイテムが見つからない場合、たとえば相手の改名などは無視する。
     * カードが挿せないだけで、ほかに影響はない。</p>
     */
    //? if >=1.21 {
    public static void allowSpeedCards(String modId, List<String> itemIds) {
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
    //?}
}
