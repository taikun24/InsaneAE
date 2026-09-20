package jp.main.taikun.insaneae.datagen;

import jp.main.taikun.insaneae.registries.ModBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * クラフトストレージのドロップ。全階層とも自分自身をそのまま落とすだけ。
 */
public class ModBlockLootProvider extends BlockLootSubProvider {

    // 1.21 から BlockLootSubProvider はレジストリ参照 (HolderLookup.Provider) を要求する。
    public ModBlockLootProvider(HolderLookup.Provider registries) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
    }

    @Override
    protected void generate() {
        knownBlocks().forEach(this::dropSelf);
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return knownBlocks();
    }

    private static List<Block> knownBlocks() {
        return Stream.of(
                        ModBlocks.allCraftingBlocks().stream().map(block -> (Block) block),
                        ModBlocks.allEnergyCells().stream().map(block -> (Block) block),
                        ModBlocks.allSolarPanels().stream().map(block -> (Block) block),
                        Stream.<Block>of(ModBlocks.QUANTUM_CPU.get(), ModBlocks.IMPROVED_CHARGER.get(),
                                ModBlocks.INSANE_INTERFACE.get(), ModBlocks.INSANE_PATTERN_PROVIDER.get()))
                .flatMap(stream -> stream)
                .toList();
    }
}
