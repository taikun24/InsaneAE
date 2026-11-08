package jp.main.taikun.insaneae.registries;

import appeng.blockentity.AEBaseBlockEntity;
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
//? if >=1.21 {
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import java.util.List;
//?}
//? if <1.21 {
/*import net.neoforged.neoforge.registries.ForgeRegistries;
import net.neoforged.neoforge.registries.RegistryObject;
*///?}

/**
 * 独自の {@link CraftingBlockEntity} 用BlockEntityType。
 *
 * AE2のクラスタ判定は型ではなく {@code instanceof CraftingBlockEntity} なので、
 * AE2のBlockEntityクラスをそのまま自前の型で登録すればCPUクラスタに合流する。
 */
public class ModBlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
    //? if >=1.21 {
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, InsaneAE.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CraftingBlockEntity>> CRAFTING_STORAGE =
    //?} else {
            /*DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, InsaneAE.MODID);

    public static final RegistryObject<BlockEntityType<CraftingBlockEntity>> CRAFTING_STORAGE =
    *///?}
            BLOCK_ENTITY_TYPES.register("crafting_storage", () -> {
                Block[] blocks = ModBlocks.allCraftingBlocks().toArray(Block[]::new);
                // build(null): 全階層ブロックで有効なCraftingBlockEntity型を構築
                // CraftingBlockEntityのコンストラクタはtypeを要求するため、遅延解決する
                // DeferredHolderを参照するラムダで供給する。実行はワールドロード時
                return BlockEntityType.Builder
                        .of((pos, state) -> new CraftingBlockEntity(type(), pos, state), blocks)
                        .build(null);
            });

    //? if >=1.21 {
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<QuantumCpuBlockEntity>> QUANTUM_CPU =
    //?} else {
    /*public static final RegistryObject<BlockEntityType<QuantumCpuBlockEntity>> QUANTUM_CPU =
    *///?}
            BLOCK_ENTITY_TYPES.register("quantum_cpu", () -> BlockEntityType.Builder
                    .of((pos, state) -> new QuantumCpuBlockEntity(quantumCpuType(), pos, state),
                            ModBlocks.QUANTUM_CPU.get())
                    .build(null));

    /**
     * 全階層のエネルギーセルで共有するBlockEntityType。
     *
     * <p>{@link EnergyCellBlockEntity} は容量・充電速度・優先度をすべて
     * {@code getBlockState().getBlock()} 経由で引くので、AE2のクラスをそのまま使える。</p>
     */
    //? if >=1.21 {
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EnergyCellBlockEntity>> ENERGY_CELL =
    //?} else {
    /*public static final RegistryObject<BlockEntityType<EnergyCellBlockEntity>> ENERGY_CELL =
    *///?}
            BLOCK_ENTITY_TYPES.register("energy_cell", () -> BlockEntityType.Builder
                    .of((pos, state) -> new EnergyCellBlockEntity(energyCellType(), pos, state),
                            ModBlocks.allEnergyCells().toArray(Block[]::new))
                    .build(null));

    /**
     * 全階層のソーラーパネルで共有するBlockEntityType。
     * 階層はブロックステートの {@link SolarPanelBlock} から引く。
     */
    //? if >=1.21 {
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SolarPanelBlockEntity>> SOLAR_PANEL =
    //?} else {
    /*public static final RegistryObject<BlockEntityType<SolarPanelBlockEntity>> SOLAR_PANEL =
    *///?}
            BLOCK_ENTITY_TYPES.register("solar_panel", () -> BlockEntityType.Builder
                    .of((pos, state) -> new SolarPanelBlockEntity(solarPanelType(), pos, state,
                                    ((SolarPanelBlock) state.getBlock()).getTier()),
                            ModBlocks.allSolarPanels().toArray(Block[]::new))
                    .build(null));

    //? if >=1.21 {
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ImprovedChargerBlockEntity>> IMPROVED_CHARGER =
    //?} else {
    /*public static final RegistryObject<BlockEntityType<ImprovedChargerBlockEntity>> IMPROVED_CHARGER =
    *///?}
            BLOCK_ENTITY_TYPES.register("improved_charger", () -> BlockEntityType.Builder
                    .of((pos, state) -> new ImprovedChargerBlockEntity(improvedChargerType(), pos, state),
                            ModBlocks.IMPROVED_CHARGER.get())
                    .build(null));

    //? if >=1.21 {
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InsaneInterfaceBlockEntity>> INSANE_INTERFACE =
    //?} else {
    /*public static final RegistryObject<BlockEntityType<InsaneInterfaceBlockEntity>> INSANE_INTERFACE =
    *///?}
            BLOCK_ENTITY_TYPES.register("insane_interface", () -> BlockEntityType.Builder
                    .of((pos, state) -> new InsaneInterfaceBlockEntity(insaneInterfaceType(), pos, state),
                            ModBlocks.INSANE_INTERFACE.get())
                    .build(null));

    //? if >=1.21 {
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InsanePatternProviderBlockEntity>> INSANE_PATTERN_PROVIDER =
    //?} else {
    /*public static final RegistryObject<BlockEntityType<InsanePatternProviderBlockEntity>> INSANE_PATTERN_PROVIDER =
    *///?}
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
    //? if >=1.21 {
    }

    /**
     * 登録済みのBlockEntityTypeを全部返す。capabilityの登録に使う ({@link ModCapabilities})。
     *
     * <p>個別に並べるとブロックを増やしたときに登録漏れに気づけない。ネットワークに
     * 繋がらないブロックができる) ので、DeferredRegisterの中身をそのまま回す。
     * レジストリ凍結後に呼ぶこと。capabilityの登録イベントはその後に来る。</p>
     */
    public static List<BlockEntityType<?>> allTypes() {
        return BLOCK_ENTITY_TYPES.getEntries().stream()
                .<BlockEntityType<?>>map(DeferredHolder::get)
                .toList();
    //?}
    }

    /**
     * 各ストレージブロックにBlockEntityTypeを結びつける。
     * ブロックとBlockEntityTypeの両方が登録済みとなるcommonSetupで呼ぶこと。
     */
    public static void bindBlockEntities() {
        BlockEntityType<CraftingBlockEntity> type = CRAFTING_STORAGE.get();
        ModBlocks.allCraftingBlocks().forEach(block ->
                block.setBlockEntity(CraftingBlockEntity.class, type, null, null));

        // Quantum CPUは完成品をまとめてMEに戻すために毎tick動く必要がある
        ModBlocks.QUANTUM_CPU.get().setBlockEntity(QuantumCpuBlockEntity.class, QUANTUM_CPU.get(), null,
                (level, pos, state, be) -> be.serverTick());

        // エネルギーセルはIGridTickableなのでグリッド側から呼ばれる。ブロックのtickerは不要
        BlockEntityType<EnergyCellBlockEntity> energyCellType = ENERGY_CELL.get();
        ModBlocks.allEnergyCells().forEach(block ->
                block.setBlockEntity(EnergyCellBlockEntity.class, energyCellType, null, null));

        // ソーラーパネルは「電力ゼロのネットワークを起動できる」必要があるので、
        // グリッドのティックマネージャではなく、アクティブなノードしか呼ばない
        // ブロック側のtickerから動かす
        BlockEntityType<SolarPanelBlockEntity> solarPanelType = SOLAR_PANEL.get();
        ModBlocks.allSolarPanels().forEach(block ->
                block.setBlockEntity(SolarPanelBlockEntity.class, solarPanelType, null,
                        (level, pos, state, be) -> be.serverTick()));
        ModBlocks.IMPROVED_CHARGER.get().setBlockEntity(
                ImprovedChargerBlockEntity.class, IMPROVED_CHARGER.get(), null, null);

        // 超特大インターフェイスはIGridTickable、つまりInterfaceLogicのTickerなので
        // グリッド側から呼ばれる。ブロックのtickerは不要
        ModBlocks.INSANE_INTERFACE.get().setBlockEntity(
                InsaneInterfaceBlockEntity.class, INSANE_INTERFACE.get(), null, null);

        // 特大パターンプロバイダーは、まとめてあるパターン更新を流すために毎tick動く
        ModBlocks.INSANE_PATTERN_PROVIDER.get().setBlockEntity(
                InsanePatternProviderBlockEntity.class, INSANE_PATTERN_PROVIDER.get(), null,
                (level, pos, state, be) -> be.serverTick());

        registerRepresentativeItems();
    }

    /**
     * BlockEntityTypeごとの「代表アイテム」をAE2に教える。
     *
     * <p>AE2の {@code AEBaseBlockEntity#getItemFromBlockEntity} は、AE2が自分の型だけを入れた表から
     * 引き、載っていない型には空気 ({@code minecraft:air}) を返す。この値は
     * アップグレード枠がどのカードを受けるか。{@code InterfaceLogic}が
     * {@code UpgradeInventories.forMachine(item, ...)} に渡す) と、名前を付けていないときの表示名に使われる。
     * 登録しないと、カードの対応付けをどれだけ足してもアップグレード枠が1枚も受け付けず、
     * 端末には「空気」と表示される。</p>
     *
     * <p>対象はブロックが1種類だけの型。階層ごとにブロックが違う型、たとえばクラフトストレージは
     * エネルギーセル・ソーラーパネル) はAE2の親クラスがブロックから名前を引くので載せない。
     * {@link #bindBlockEntities()}と同じくcommonSetupで呼ぶ。ワールドの読み込み、つまりBEの生成より前だから。</p>
     */
    private static void registerRepresentativeItems() {
        AEBaseBlockEntity.registerBlockEntityItem(QUANTUM_CPU.get(), ModBlocks.QUANTUM_CPU.get().asItem());
        AEBaseBlockEntity.registerBlockEntityItem(IMPROVED_CHARGER.get(), ModBlocks.IMPROVED_CHARGER.get().asItem());
        AEBaseBlockEntity.registerBlockEntityItem(INSANE_INTERFACE.get(), ModBlocks.INSANE_INTERFACE.get().asItem());
        AEBaseBlockEntity.registerBlockEntityItem(INSANE_PATTERN_PROVIDER.get(),
                ModBlocks.INSANE_PATTERN_PROVIDER.get().asItem());
    }
}
