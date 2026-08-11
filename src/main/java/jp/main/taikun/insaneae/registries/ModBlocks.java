package jp.main.taikun.insaneae.registries;

import net.minecraft.core.registries.Registries;
import appeng.block.crafting.CraftingUnitBlock;
import appeng.block.networking.EnergyCellBlock;
import appeng.block.networking.EnergyCellBlockItem;
import jp.main.taikun.insaneae.InsaneAE;
import jp.main.taikun.insaneae.crafting.InsaneAcceleratorType;
import jp.main.taikun.insaneae.crafting.BigIntegerCraftingUnitType;
import jp.main.taikun.insaneae.crafting.InsaneCraftingUnitBlock;
import jp.main.taikun.insaneae.crafting.InsaneCraftingUnitType;
import jp.main.taikun.insaneae.util.TieredBlockItem;
import jp.main.taikun.insaneae.util.TieredNames;
import jp.main.taikun.insaneae.charger.ImprovedChargerBlock;
import jp.main.taikun.insaneae.energy.InsaneEnergyCellTier;
import jp.main.taikun.insaneae.energy.SolarPanelBlock;
import jp.main.taikun.insaneae.energy.SolarPanelTier;
import jp.main.taikun.insaneae.iface.InsaneInterfaceBlock;
import jp.main.taikun.insaneae.provider.InsanePatternProviderBlock;
import jp.main.taikun.insaneae.quantum.QuantumCpuBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class ModBlocks {
    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, InsaneAE.MODID);
    private static final DeferredRegister<Item> BLOCK_ITEMS =
            DeferredRegister.create(Registries.ITEM, InsaneAE.MODID);

    /** 各クラフトストレージ階層 → 登録済みブロック。 */
    public static final Map<InsaneCraftingUnitType, DeferredHolder<Block, CraftingUnitBlock>> CRAFTING_STORAGE =
            new EnumMap<>(InsaneCraftingUnitType.class);

    /** 各クラフト協調処理ユニット (アクセラレータ) 階層 → 登録済みブロック。 */
    public static final Map<InsaneAcceleratorType, DeferredHolder<Block, CraftingUnitBlock>> CRAFTING_ACCELERATOR =
            new EnumMap<>(InsaneAcceleratorType.class);

    /** 各エネルギーセル階層 → 登録済みブロック。 */
    public static final Map<InsaneEnergyCellTier, DeferredHolder<Block, EnergyCellBlock>> ENERGY_CELLS =
            new EnumMap<>(InsaneEnergyCellTier.class);

    /** 各ソーラーパネル階層 → 登録済みブロック。 */
    public static final Map<SolarPanelTier, DeferredHolder<Block, SolarPanelBlock>> SOLAR_PANELS =
            new EnumMap<>(SolarPanelTier.class);

    /** パターンプロバイダ + 分子組立装置を合体させた自己完結型のクラフト機。 */
    public static final DeferredHolder<Block, QuantumCpuBlock> QUANTUM_CPU =
            BLOCKS.register("quantum_cpu", QuantumCpuBlock::new);

    /** ACOの理論上限容量を持つ、通常AE2クラフトCPU用のストレージブロック。 */
    public static final DeferredHolder<Block, CraftingUnitBlock> BIG_INTEGER_CPU =
            BLOCKS.register("big_integer_cpu",
                    () -> new CraftingUnitBlock(BigIntegerCraftingUnitType.INSTANCE));

    /** AE2 のチャージャーの限界突破版。 */
    public static final DeferredHolder<Block, ImprovedChargerBlock> IMPROVED_CHARGER =
            BLOCKS.register("improved_charger", ImprovedChargerBlock::new);

    /** ME インターフェイスの限界突破版 (9x9 枠 × 1 枠 21 億)。 */
    public static final DeferredHolder<Block, InsaneInterfaceBlock> INSANE_INTERFACE =
            BLOCKS.register("insane_interface", InsaneInterfaceBlock::new);

    /** パターンプロバイダの限界突破版 (Quantum CPU と同じ 1620 枠)。加工パターンの置き場。 */
    public static final DeferredHolder<Block, InsanePatternProviderBlock> INSANE_PATTERN_PROVIDER =
            BLOCKS.register("insane_pattern_provider", InsanePatternProviderBlock::new);

    static {
        // 表示名は階層ごとの lang キーではなく「書式キー + 階層ラベル」で作る → TieredNames。
        for (InsaneCraftingUnitType type : InsaneCraftingUnitType.values()) {
            DeferredHolder<Block, CraftingUnitBlock> block = BLOCKS.register(type.blockId(),
                    () -> new InsaneCraftingUnitBlock(type, TieredNames.CRAFTING_STORAGE, type.label()));
            BLOCK_ITEMS.register(type.blockId(),
                    () -> new TieredBlockItem(block.get(), new Item.Properties()));
            // ICraftingUnitType.getItemFromType() が返すアイテムを、登録された BlockItem に結びつける。
            type.setItem(() -> block.get().asItem());
            CRAFTING_STORAGE.put(type, block);
        }
        for (InsaneAcceleratorType type : InsaneAcceleratorType.values()) {
            DeferredHolder<Block, CraftingUnitBlock> block = BLOCKS.register(type.blockId(),
                    () -> new InsaneCraftingUnitBlock(type, TieredNames.CRAFTING_ACCELERATOR, type.label()));
            BLOCK_ITEMS.register(type.blockId(),
                    () -> new TieredBlockItem(block.get(), new Item.Properties()));
            type.setItem(() -> block.get().asItem());
            CRAFTING_ACCELERATOR.put(type, block);
        }
        for (InsaneEnergyCellTier tier : InsaneEnergyCellTier.values()) {
            // AE2 の EnergyCellBlock / EnergyCellBlockItem をそのまま使う。
            // AE2 側は「registry を走査して EnergyCellBlockItem なら fill_level を登録」する作りなので、
            // アイテムモデルの残量表示もクライアント側の追加処理なしで動く。
            DeferredHolder<Block, EnergyCellBlock> block = BLOCKS.register(tier.id(),
                    () -> new EnergyCellBlock(tier.maxPower(), tier.chargeRate(), tier.priority()));
            BLOCK_ITEMS.register(tier.id(),
                    () -> new EnergyCellBlockItem(block.get(), new Item.Properties()));
            ENERGY_CELLS.put(tier, block);
        }
        for (SolarPanelTier tier : SolarPanelTier.values()) {
            DeferredHolder<Block, SolarPanelBlock> block =
                    BLOCKS.register(tier.id(), () -> new SolarPanelBlock(tier));
            BLOCK_ITEMS.register(tier.id(), () -> new BlockItem(block.get(), new Item.Properties()));
            SOLAR_PANELS.put(tier, block);
        }
        BLOCK_ITEMS.register("quantum_cpu", () -> new BlockItem(QUANTUM_CPU.get(), new Item.Properties()));
        // サバイバルレシピは追加せず、クリエイティブタブから検証用ストレージを取得できるようにする。
        BLOCK_ITEMS.register("big_integer_cpu", () -> new BlockItem(BIG_INTEGER_CPU.get(), new Item.Properties()));
        BigIntegerCraftingUnitType.INSTANCE.setItem(() -> BIG_INTEGER_CPU.get().asItem());
        BLOCK_ITEMS.register("improved_charger",
                () -> new BlockItem(IMPROVED_CHARGER.get(), new Item.Properties()));
        BLOCK_ITEMS.register("insane_interface",
                () -> new BlockItem(INSANE_INTERFACE.get(), new Item.Properties()));
        BLOCK_ITEMS.register("insane_pattern_provider",
                () -> new BlockItem(INSANE_PATTERN_PROVIDER.get(), new Item.Properties()));
    }

    /** ストレージ + アクセラレータの全ブロック (BlockEntityType やドロップ生成用)。 */
    public static List<CraftingUnitBlock> allCraftingBlocks() {
        return Stream.concat(
                        Stream.concat(CRAFTING_STORAGE.values().stream(), CRAFTING_ACCELERATOR.values().stream()),
                        Stream.of(BIG_INTEGER_CPU))
                .map(DeferredHolder::get)
                .toList();
    }

    /** 全階層のエネルギーセルブロック。 */
    public static List<EnergyCellBlock> allEnergyCells() {
        return ENERGY_CELLS.values().stream().map(DeferredHolder::get).toList();
    }

    /** 全階層のソーラーパネルブロック。 */
    public static List<SolarPanelBlock> allSolarPanels() {
        return SOLAR_PANELS.values().stream().map(DeferredHolder::get).toList();
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        BLOCK_ITEMS.register(bus);
    }
}
