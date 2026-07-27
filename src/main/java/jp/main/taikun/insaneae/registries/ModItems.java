package jp.main.taikun.insaneae.registries;

import jp.main.taikun.insaneae.InsaneAE;
import jp.main.taikun.insaneae.crafting.InsaneCraftingUnitType;
import jp.main.taikun.insaneae.util.TieredItem;
import jp.main.taikun.insaneae.util.TieredNames;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

/**
 * 各階層のセルコンポーネント。
 *
 * <p>AE2 / MEGA Cells と同じ構成で、コンポーネント 1 個 + クラフトユニットで
 * その階層のクラフトストレージになる。コンポーネント自体は下位 4 個から作る。</p>
 */
public class ModItems {
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, InsaneAE.MODID);

    /** 各階層 → セルコンポーネント。 */
    public static final Map<InsaneCraftingUnitType, RegistryObject<Item>> CELL_COMPONENTS =
            new EnumMap<>(InsaneCraftingUnitType.class);

    static {
        for (InsaneCraftingUnitType type : InsaneCraftingUnitType.values()) {
            // 表示名は階層ごとの lang キーではなく「書式キー + 階層ラベル」で作る → TieredNames。
            CELL_COMPONENTS.put(type, ITEMS.register(type.cellComponentId(),
                    () -> new TieredItem(new Item.Properties(), TieredNames.CELL_COMPONENT, type.label())));
        }
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
