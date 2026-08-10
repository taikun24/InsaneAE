package jp.main.taikun.insaneae.provider;

import appeng.api.networking.IGridNodeListener;
import appeng.api.stacks.AEItemKey;
import appeng.api.util.AECableType;
import appeng.blockentity.ServerTickingBlockEntity;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;
import jp.main.taikun.insaneae.menu.InsanePatternProviderMenu;
import jp.main.taikun.insaneae.quantum.QuantumCpuBlockEntity;
import jp.main.taikun.insaneae.registries.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;
import java.util.List;

/**
 * 特大パターンプロバイダー — AE2 のパターンプロバイダのパターン枠を
 * Quantum CPU と同じ {@value QuantumCpuBlockEntity#PATTERN_SLOTS} 枠に増やしたもの。
 *
 * <p>ページ数・1 ページの枠数も Quantum CPU と同じ
 * ({@value QuantumCpuBlockEntity#PATTERN_PAGES} ページ ×
 * {@value QuantumCpuBlockEntity#PATTERN_SLOTS_PER_PAGE} 枠)。
 * Quantum CPU と違って自分では組まず、<b>全部のパターンを普通のパターンプロバイダとして</b>
 * 隣接インベントリ (や分子組立装置) へ押し出す。加工パターンの置き場はこちら —
 * Quantum CPU は加工パターンを受け付けない ({@code QuantumCpuLogic} のフィルタ)。</p>
 *
 * <p>毎 tick の {@link #serverTick()} は、1620 枠に耐えるためにまとめてある
 * パターン更新を流すためだけにある → {@link InsanePatternProviderLogic}。</p>
 */
public class InsanePatternProviderBlockEntity extends AENetworkedBlockEntity
        implements PatternProviderLogicHost, ServerTickingBlockEntity {

    private final InsanePatternProviderLogic logic =
            new InsanePatternProviderLogic(getMainNode(), this, QuantumCpuBlockEntity.PATTERN_SLOTS);

    public InsanePatternProviderBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        getMainNode().setIdlePowerUsage(6.0);
    }

    @Override
    public void serverTick() {
        logic.flushPatternUpdate();
    }

    // ------------------------------------------------- PatternProviderLogicHost

    @Override
    public PatternProviderLogic getLogic() {
        return logic;
    }

    @Override
    public EnumSet<Direction> getTargets() {
        // 向きの概念を持たない。全方向に押し出せる。
        return EnumSet.allOf(Direction.class);
    }

    @Override
    public AEItemKey getTerminalIcon() {
        return AEItemKey.of(ModBlocks.INSANE_PATTERN_PROVIDER.get());
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return new ItemStack(ModBlocks.INSANE_PATTERN_PROVIDER.get());
    }

    @Override
    public void openMenu(Player player, MenuHostLocator locator) {
        MenuOpener.open(InsanePatternProviderMenu.TYPE, player, locator);
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        MenuOpener.returnTo(InsanePatternProviderMenu.TYPE, player, subMenu.getLocator());
    }

    // ------------------------------------------------------------ BlockEntity

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        logic.onMainNodeStateChanged();
    }

    @Override
    public void onReady() {
        super.onReady();
        logic.updatePatterns();
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.SMART;
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        logic.writeToNBT(data, registries);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        logic.readFromNBT(data, registries);
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        logic.addDrops(drops);
    }

    @Override
    public void clearContent() {
        super.clearContent();
        logic.clearContent();
    }
}
