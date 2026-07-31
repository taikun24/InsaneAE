package jp.main.taikun.insaneae.registries;

import net.minecraft.core.registries.Registries;
import jp.main.taikun.insaneae.InsaneAE;
import jp.main.taikun.insaneae.menu.QuantumCpuMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * 独自 GUI の {@link MenuType}。
 *
 * <p>AE2 の {@code MenuTypeBuilder} は {@code ae2} 名前空間に登録してしまうので使わず、
 * {@link QuantumCpuMenu} 側で作った {@code MenuType} をここで {@code insaneae} 名前空間に登録する。</p>
 */
public class ModMenus {
    private static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, InsaneAE.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<QuantumCpuMenu>> QUANTUM_CPU =
            MENU_TYPES.register("quantum_cpu", () -> QuantumCpuMenu.TYPE);

    public static void register(IEventBus bus) {
        MENU_TYPES.register(bus);
    }
}
