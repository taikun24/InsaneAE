package jp.main.taikun.insaneae.registries;

import jp.main.taikun.insaneae.InsaneAE;
import jp.main.taikun.insaneae.crafting.InsaneCraftingUnitType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModCreativeTabs {
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, InsaneAE.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + InsaneAE.MODID))
            .icon(() -> new ItemStack(ModBlocks.QUANTUM_CPU.get()))
            .displayItems((params, output) -> {
                ModBlocks.CRAFTING_STORAGE.values().forEach(block -> output.accept(block.get()));
                ModBlocks.CRAFTING_ACCELERATOR.values().forEach(block -> output.accept(block.get()));
                output.accept(ModBlocks.QUANTUM_CPU.get());
                output.accept(ModBlocks.INSANE_PATTERN_PROVIDER.get());
                output.accept(ModUpgrades.QUANTUM_ACCELERATION_CARD.get());
                output.accept(ModBlocks.IMPROVED_CHARGER.get());
                output.accept(ModBlocks.INSANE_INTERFACE.get());
                // AE2 の EnergyCellBlock#addToMainCreativeTab は「空」と「満充電」の 2 個を出す。
                ModBlocks.allEnergyCells().forEach(cell -> cell.addToMainCreativeTab(params, output));
                ModBlocks.allSolarPanels().forEach(output::accept);
                ModUpgrades.SPEED_CARDS.values().forEach(card -> output.accept(card.get()));
                ModItems.CELL_COMPONENTS.values().forEach(item -> output.accept(item.get()));
                ModCells.ITEM_CELLS.values().forEach(cell -> output.accept(cell.get()));
                ModCells.FLUID_CELLS.values().forEach(cell -> output.accept(cell.get()));
                ModCells.PORTABLE_ITEM_CELLS.values().forEach(cell -> output.accept(cell.get()));
                ModCells.PORTABLE_FLUID_CELLS.values().forEach(cell -> output.accept(cell.get()));
                output.accept(ModCells.CREATIVE_CELL.get());
                if (ModList.get().isLoaded(InsaneAE.APPMEK_MODID)) {
                    jp.main.taikun.insaneae.integration.appmek.AppMekCells.addToCreativeTab(output::accept);
                }
            })
            .build());

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }
}
