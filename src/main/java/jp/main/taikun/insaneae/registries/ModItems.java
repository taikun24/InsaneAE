package jp.main.taikun.insaneae.registries;

import jp.main.taikun.insaneae.InsaneAE;
import jp.main.taikun.insaneae.crafting.InsaneCraftingUnitType;
import jp.main.taikun.insaneae.util.TieredItem;
import jp.main.taikun.insaneae.util.TieredNames;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;
//? if >=1.21 {
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
//?}
//? if <1.21 {
/*import net.neoforged.neoforge.registries.ForgeRegistries;
import net.neoforged.neoforge.registries.RegistryObject;
*///?}

/**
 * 各階層のセルコンポーネント。
 *
 * <p>AE2 / MEGA Cells と同じ構成で、コンポーネント 1 個 + クラフトユニットで
 * その階層のクラフトストレージになる。コンポーネント自体は下位 4 個から作る。</p>
 */
public class ModItems {
    private static final DeferredRegister<Item> ITEMS =
    //? if >=1.21 {
            DeferredRegister.create(Registries.ITEM, InsaneAE.MODID);

    public static final Map<InsaneCraftingUnitType, DeferredHolder<Item, Item>> CELL_COMPONENTS =
    //?} else {
            /*DeferredRegister.create(ForgeRegistries.ITEMS, InsaneAE.MODID);

    public static final Map<InsaneCraftingUnitType, RegistryObject<Item>> CELL_COMPONENTS =
    *///?}
            new EnumMap<>(InsaneCraftingUnitType.class);

    /**
     * Insane プロセッサの金型。MEGA の集積回路の金型と同じく、既存の金型 2 枚を
     * 刻印機にかけて作る (中段は量子もつれ特異点)。鉄ブロックで複製できる。
     */
    //? if >=1.21 {
    public static final DeferredHolder<Item, Item> INSANE_PROCESSOR_PRESS =
    //?} else {
    /*public static final RegistryObject<Item> INSANE_PROCESSOR_PRESS =
    *///?}
            ITEMS.register("insane_processor_press", () -> new Item(new Item.Properties()));
    /** Insane 回路。金型 + 特異点を刻印機で。 */
    //? if >=1.21 {
    public static final DeferredHolder<Item, Item> PRINTED_INSANE_PROCESSOR =
    //?} else {
    /*public static final RegistryObject<Item> PRINTED_INSANE_PROCESSOR =
    *///?}
            ITEMS.register("printed_insane_processor", () -> new Item(new Item.Properties()));
    /**
     * Insane プロセッサ。Insane 回路 + 集積プロセッサ + シリコン回路を刻印機で。
     * Quantum CPU など最上位のブロックの材料 (1 個につき特異点 1 と集積プロセッサ 1 を食う)。
     */
    //? if >=1.21 {
    public static final DeferredHolder<Item, Item> INSANE_PROCESSOR =
    //?} else {
    /*public static final RegistryObject<Item> INSANE_PROCESSOR =
    *///?}
            ITEMS.register("insane_processor", () -> new Item(new Item.Properties()));

    static {
        for (InsaneCraftingUnitType type : InsaneCraftingUnitType.values()) {
            // 表示名は階層ごとの lang キーではなく「書式キー + 階層ラベル」で作る (TieredNames を参照)。
            CELL_COMPONENTS.put(type, ITEMS.register(type.cellComponentId(),
                    () -> new TieredItem(new Item.Properties(), TieredNames.CELL_COMPONENT, type.label())));
        }
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
