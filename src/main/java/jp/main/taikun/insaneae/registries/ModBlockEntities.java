package jp.main.taikun.insaneae.registries;

import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.blockentity.networking.EnergyCellBlockEntity;
import jp.main.taikun.insaneae.InsaneAE;
import jp.main.taikun.insaneae.charger.ImprovedChargerBlockEntity;
import jp.main.taikun.insaneae.energy.SolarPanelBlock;
import jp.main.taikun.insaneae.energy.SolarPanelBlockEntity;
import jp.main.taikun.insaneae.quantum.QuantumCpuBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 独自の {@link CraftingBlockEntity} 用 BlockEntityType。
 *
 * AE2 のクラスタ判定は型ではなく {@code instanceof CraftingBlockEntity} なので、
 * AE2 の BlockEntity クラスをそのまま自前の型で登録すれば CPU クラスタに合流する。
 */
public class ModBlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, InsaneAE.MODID);

    public static final RegistryObject<BlockEntityType<CraftingBlockEntity>> CRAFTING_STORAGE =
            BLOCK_ENTITY_TYPES.register("crafting_storage", () -> {
                Block[] blocks = ModBlocks.allCraftingBlocks().toArray(Block[]::new);
                // build(null): 全階層ブロックで有効な CraftingBlockEntity 型を構築。
                // CraftingBlockEntity のコンストラクタは type を要求するため、遅延解決する
                // RegistryObject を参照するラムダで供給する (実行はワールドロード時)。
                return BlockEntityType.Builder
                        .of((pos, state) -> new CraftingBlockEntity(type(), pos, state), blocks)
                        .build(null);
            });

    public static final RegistryObject<BlockEntityType<QuantumCpuBlockEntity>> QUANTUM_CPU =
            BLOCK_ENTITY_TYPES.register("quantum_cpu", () -> BlockEntityType.Builder
                    .of((pos, state) -> new QuantumCpuBlockEntity(quantumCpuType(), pos, state),
                            ModBlocks.QUANTUM_CPU.get())
                    .build(null));

    /**
     * 全階層のエネルギーセルで共有する BlockEntityType。
     *
     * <p>{@link EnergyCellBlockEntity} は容量・充電速度・優先度をすべて
     * {@code getBlockState().getBlock()} 経由で引くので、AE2 のクラスをそのまま使える。</p>
     */
    public static final RegistryObject<BlockEntityType<EnergyCellBlockEntity>> ENERGY_CELL =
            BLOCK_ENTITY_TYPES.register("energy_cell", () -> BlockEntityType.Builder
                    .of((pos, state) -> new EnergyCellBlockEntity(energyCellType(), pos, state),
                            ModBlocks.allEnergyCells().toArray(Block[]::new))
                    .build(null));

    /**
     * 全階層のソーラーパネルで共有する BlockEntityType。
     * 階層はブロックステートの {@link SolarPanelBlock} から引く。
     */
    public static final RegistryObject<BlockEntityType<SolarPanelBlockEntity>> SOLAR_PANEL =
            BLOCK_ENTITY_TYPES.register("solar_panel", () -> BlockEntityType.Builder
                    .of((pos, state) -> new SolarPanelBlockEntity(solarPanelType(), pos, state,
                                    ((SolarPanelBlock) state.getBlock()).getTier()),
                            ModBlocks.allSolarPanels().toArray(Block[]::new))
                    .build(null));

    public static final RegistryObject<BlockEntityType<ImprovedChargerBlockEntity>> IMPROVED_CHARGER =
            BLOCK_ENTITY_TYPES.register("improved_charger", () -> BlockEntityType.Builder
                    .of((pos, state) -> new ImprovedChargerBlockEntity(improvedChargerType(), pos, state),
                            ModBlocks.IMPROVED_CHARGER.get())
                    .build(null));

    /** 自己参照コンパイルエラーを避けるための遅延アクセサ。 */
    private static BlockEntityType<CraftingBlockEntity> type() {
        return CRAFTING_STORAGE.get();
    }

    private static BlockEntityType<QuantumCpuBlockEntity> quantumCpuType() {
        return QUANTUM_CPU.get();
    }

    private static BlockEntityType<EnergyCellBlockEntity> energyCellType() {
        return ENERGY_CELL.get();
    }

    private static BlockEntityType<SolarPanelBlockEntity> solarPanelType() {
        return SOLAR_PANEL.get();
    }

    private static BlockEntityType<ImprovedChargerBlockEntity> improvedChargerType() {
        return IMPROVED_CHARGER.get();
    }

    public static void register(IEventBus bus) {
        BLOCK_ENTITY_TYPES.register(bus);
    }

    /**
     * 各ストレージブロックに BlockEntityType を結びつける。
     * ブロックと BlockEntityType の両方が登録済みとなる commonSetup で呼ぶこと。
     */
    public static void bindBlockEntities() {
        BlockEntityType<CraftingBlockEntity> type = CRAFTING_STORAGE.get();
        ModBlocks.allCraftingBlocks().forEach(block ->
                block.setBlockEntity(CraftingBlockEntity.class, type, null, null));

        // Quantum CPU は完成品をまとめて ME に戻すために毎 tick 動く必要がある。
        ModBlocks.QUANTUM_CPU.get().setBlockEntity(QuantumCpuBlockEntity.class, QUANTUM_CPU.get(), null,
                (level, pos, state, be) -> be.serverTick());

        // エネルギーセルは IGridTickable なのでグリッド側から呼ばれる (ブロックの ticker は不要)。
        BlockEntityType<EnergyCellBlockEntity> energyCellType = ENERGY_CELL.get();
        ModBlocks.allEnergyCells().forEach(block ->
                block.setBlockEntity(EnergyCellBlockEntity.class, energyCellType, null, null));

        // ソーラーパネルは「電力ゼロのネットワークを起動できる」必要があるので、
        // グリッドのティックマネージャ (アクティブなノードしか呼ばない) ではなく
        // ブロック側の ticker から動かす。
        BlockEntityType<SolarPanelBlockEntity> solarPanelType = SOLAR_PANEL.get();
        ModBlocks.allSolarPanels().forEach(block ->
                block.setBlockEntity(SolarPanelBlockEntity.class, solarPanelType, null,
                        (level, pos, state, be) -> be.serverTick()));
        ModBlocks.IMPROVED_CHARGER.get().setBlockEntity(
                ImprovedChargerBlockEntity.class, IMPROVED_CHARGER.get(), null, null);
    }
}
