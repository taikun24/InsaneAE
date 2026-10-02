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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import jp.main.taikun.insaneae.integration.AddonIntegration;
import jp.main.taikun.insaneae.integration.AddonIntegrations;
import net.minecraft.world.item.Items;

/**
 * 加速カード (アップグレードカード) の登録。
 *
 * <p>取り付け可能な機械は AE2 の加速カードと同じ顔ぶれのうち、
 * 速度倍率を実装済みのものに限っている ({@link #SUPPORTED_MACHINES})。</p>
 */
public class ModUpgrades {
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, InsaneAE.MODID);

    /** 1 機械あたりの取り付け上限。倍率が大きいので 1 枚で十分。 */
    private static final int MAX_INSTALLED = 1;

    /** 速度倍率を実装済みの機械。 */
    private static final List<ItemLike> SUPPORTED_MACHINES = List.of(
            AEParts.IMPORT_BUS,
            AEParts.EXPORT_BUS,
            AEBlocks.MOLECULAR_ASSEMBLER,
            AEBlocks.INSCRIBER,
            AEBlocks.IO_PORT);

    public static final Map<InsaneSpeedCardType, RegistryObject<Item>> SPEED_CARDS =
            new EnumMap<>(InsaneSpeedCardType.class);

    /** Quantum CPU 専用の加速カード。1 枚ごとに組み立て速度が 256 倍。 */
    public static final RegistryObject<Item> QUANTUM_ACCELERATION_CARD =
            ITEMS.register("quantum_acceleration_card",
                    () -> new QuantumAccelerationCardItem(new Item.Properties()));

    /** Quantum CPU 専用のタスク統合カード。まとめ 1 回をクラスタ予算の 1 操作として数えさせる。 */
    public static final RegistryObject<Item> TASK_FUSION_CARD =
            ITEMS.register("task_fusion_card",
                    () -> new TaskFusionCardItem(new Item.Properties()));

    static {
        for (InsaneSpeedCardType type : InsaneSpeedCardType.values()) {
            RegistryObject<Item> card = ITEMS.register(type.id(),
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
        Upgrades.add(TASK_FUSION_CARD.get(), ModBlocks.QUANTUM_CPU.get(), 1);

        registerCellUpgrades();
        AddonIntegrations.active().forEach(AddonIntegration::registerUpgrades);
    }

    /**
     * 他 Mod の「AE2 の加速カードが挿せる機械」にもこちらの加速カードを挿せるようにする。
     * 各アドオン連携 ({@code integration/}) の {@code registerUpgrades} から呼ぶ。
     *
     * <p>挿せるだけでは意味が無いので、対象は<b>速度倍率の Mixin を用意した機械だけ</b>
     * (mixin/compat の Ex〜・AAE〜 を参照。バス系は AE2 の基底クラスの Mixin がそのまま効く)。
     * どの Mod もコンパイル依存には入れず、登録名からアイテムを引く。
     * アイテムが見つからない場合 (相手の改名など) は静かに飛ばす —
     * カードが挿せないだけで、壊れはしない。</p>
     */
    public static void allowSpeedCards(String modId, List<String> itemIds) {
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

        for (RegistryObject<Item> cell : ModCells.ITEM_CELLS.values()) {
            addItemCellCards(cell.get(), cells);
        }
        for (RegistryObject<Item> cell : ModCells.FLUID_CELLS.values()) {
            addFluidCellCards(cell.get(), cells);
        }
        for (RegistryObject<Item> cell : ModCells.PORTABLE_ITEM_CELLS.values()) {
            addItemCellCards(cell.get(), portables);
            addPortableEnergyCards(cell.get(), portables);
        }
        for (RegistryObject<Item> cell : ModCells.PORTABLE_FLUID_CELLS.values()) {
            addFluidCellCards(cell.get(), portables);
            addPortableEnergyCards(cell.get(), portables);
        }
        // 強化クリエイティブセルは AE2 のクリエイティブセルと同じくカード無し。
    }

    /**
     * ポータブルセルの電力カード。AE2 のエネルギーカードに加えて
     * <b>MEGA Cells の Greater Energy Card</b> も受ける。
     *
     * <p>枚数 (2) もツールチップのまとめ先も MEGA が自分のポータブルセルにしている登録と同じ。
     * 効き目は AE2 の {@code PortableCellItem} 側が面倒を見るので、
     * <b>挿せるようにするだけで容量が増える</b> (MEGA Cells は必須依存なので分岐は要らない)。</p>
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

    /** 液体・化学物質のセルが受けるカード (あいまいカードはスタック NBT の概念が無いので除く)。 */
    public static void addFluidCellCards(ItemLike cell, String tooltipGroup) {
        Upgrades.add(AEItems.INVERTER_CARD, cell, 1, tooltipGroup);
        Upgrades.add(AEItems.EQUAL_DISTRIBUTION_CARD, cell, 1, tooltipGroup);
        Upgrades.add(AEItems.VOID_CARD, cell, 1, tooltipGroup);
    }
}
