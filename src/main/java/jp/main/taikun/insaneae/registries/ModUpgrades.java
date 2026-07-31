package jp.main.taikun.insaneae.registries;

import net.minecraft.core.registries.Registries;
import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import jp.main.taikun.insaneae.InsaneAE;
import jp.main.taikun.insaneae.quantum.QuantumCpuBlockEntity;
import jp.main.taikun.insaneae.upgrade.InsaneSpeedCardItem;
import jp.main.taikun.insaneae.upgrade.InsaneSpeedCardType;
import jp.main.taikun.insaneae.upgrade.QuantumAccelerationCardItem;
import net.minecraft.world.item.Item;
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
    }
}
