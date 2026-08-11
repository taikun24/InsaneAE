package jp.main.taikun.insaneae.registries;

import net.minecraft.core.registries.Registries;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.blockentity.networking.EnergyCellBlockEntity;
import jp.main.taikun.insaneae.InsaneAE;
import jp.main.taikun.insaneae.charger.ImprovedChargerBlockEntity;
import jp.main.taikun.insaneae.energy.SolarPanelBlock;
import jp.main.taikun.insaneae.energy.SolarPanelBlockEntity;
import jp.main.taikun.insaneae.iface.InsaneInterfaceBlockEntity;
import jp.main.taikun.insaneae.provider.InsanePatternProviderBlockEntity;
import jp.main.taikun.insaneae.quantum.QuantumCpuBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.List;

/**
 * 独自の {@link CraftingBlockEntity} 用 BlockEntityType。
 *
 * AE2 のクラスタ判定は型ではなく {@code instanceof CraftingBlockEntity} なので、
 * AE2 の BlockEntity クラスをそのまま自前の型で登録すれば CPU クラスタに合流する。
 */
public class ModBlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, InsaneAE.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CraftingBlockEntity>> CRAFTING_STORAGE =
            BLOCK_ENTITY_TYPES.register("crafting_storage", () -> {
                Block[] blocks = ModBlocks.allCraftingBlocks().toArray(Block[]::new);
                // build(null): 全階層ブロックで有効な CraftingBlockEntity 型を構築。
                // CraftingBlockEntity のコンストラクタは type を要求するため、遅延解決する
                // DeferredHolder を参照するラムダで供給する (実行はワールドロード時)。
                return BlockEntityType.Builder
                        .of((pos, state) -> new CraftingBlockEntity(type(), pos, state), blocks)
                        .build(null);
            });

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<QuantumCpuBlockEntity>> QUANTUM_CPU =
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
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EnergyCellBlockEntity>> ENERGY_CELL =
            BLOCK_ENTITY_TYPES.register("energy_cell", () -> BlockEntityType.Builder
                    .of((pos, state) -> new EnergyCellBlockEntity(energyCellType(), pos, state),
                            ModBlocks.allEnergyCells().toArray(Block[]::new))
                    .build(null));

    /**
     * 全階層のソーラーパネルで共有する BlockEntityType。
     * 階層はブロックステートの {@link SolarPanelBlock} から引く。
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SolarPanelBlockEntity>> SOLAR_PANEL =
            BLOCK_ENTITY_TYPES.register("solar_panel", () -> BlockEntityType.Builder
                    .of((pos, state) -> new SolarPanelBlockEntity(solarPanelType(), pos, state,
                                    ((SolarPanelBlock) state.getBlock()).getTier()),
                            ModBlocks.allSolarPanels().toArray(Block[]::new))
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ImprovedChargerBlockEntity>> IMPROVED_CHARGER =
            BLOCK_ENTITY_TYPES.register("improved_charger", () -> BlockEntityType.Builder
                    .of((pos, state) -> new ImprovedChargerBlockEntity(improvedChargerType(), pos, state),
                            ModBlocks.IMPROVED_CHARGER.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InsaneInterfaceBlockEntity>> INSANE_INTERFACE =
            BLOCK_ENTITY_TYPES.register("insane_interface", () -> BlockEntityType.Builder
                    .of((pos, state) -> new InsaneInterfaceBlockEntity(insaneInterfaceType(), pos, state),
                            ModBlocks.INSANE_INTERFACE.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InsanePatternProviderBlockEntity>> INSANE_PATTERN_PROVIDER =
            BLOCK_ENTITY_TYPES.register("insane_pattern_provider", () -> BlockEntityType.Builder
                    .of((pos, state) -> new InsanePatternProviderBlockEntity(insanePatternProviderType(), pos, state),
                            ModBlocks.INSANE_PATTERN_PROVIDER.get())
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

    private static BlockEntityType<InsaneInterfaceBlockEntity> insaneInterfaceType() {
        return INSANE_INTERFACE.get();
    }

    private static BlockEntityType<InsanePatternProviderBlockEntity> insanePatternProviderType() {
        return INSANE_PATTERN_PROVIDER.get();
    }

    public static void register(IEventBus bus) {
        BLOCK_ENTITY_TYPES.register(bus);
    }

    /**
     * 登録済みの BlockEntityType を全部返す。capability の登録に使う ({@link ModCapabilities})。
     *
     * <p>個別に並べるとブロックを増やしたときに<b>登録漏れが黙って起きる</b> (ネットワークに
     * 繋がらないブロックができる) ので、DeferredRegister の中身をそのまま回す。
     * レジストリ凍結後に呼ぶこと (capability の登録イベントはその後に来る)。</p>
     */
    public static List<BlockEntityType<?>> allTypes() {
        return BLOCK_ENTITY_TYPES.getEntries().stream()
                .<BlockEntityType<?>>map(DeferredHolder::get)
                .toList();
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

        // 超特大インターフェイスの補充処理は IGridTickable (InterfaceLogic の Ticker) が
        // グリッド側から呼ぶ。ブロックの ticker は吸い込みモード専用
        // (グリッドのティックマネージャは 1 ノード 1 ティッカーで、InterfaceLogic が使用済み)。
        ModBlocks.INSANE_INTERFACE.get().setBlockEntity(
                InsaneInterfaceBlockEntity.class, INSANE_INTERFACE.get(), null,
                (level, pos, state, be) -> be.serverTick());

        // 特大パターンプロバイダーは、まとめてあるパターン更新を流すために毎 tick 動く。
        ModBlocks.INSANE_PATTERN_PROVIDER.get().setBlockEntity(
                InsanePatternProviderBlockEntity.class, INSANE_PATTERN_PROVIDER.get(), null,
                (level, pos, state, be) -> be.serverTick());
    }
}
