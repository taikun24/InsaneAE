package jp.main.taikun.insaneae.iface;

import appeng.block.AEBaseEntityBlock;
import appeng.menu.locator.MenuLocators;
import appeng.util.InteractionUtil;
import jp.main.taikun.insaneae.integration.AddonIntegrations;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 超特大インターフェイスのブロック。中身は {@link InsaneInterfaceBlockEntity}。
 *
 * <p>AE2 の {@code InterfaceBlock} と同じで、右クリックで画面を開くだけ
 * (メモリーカード等の持ち物ありの操作は {@link AEBaseEntityBlock} が先に処理する)。</p>
 */
public class InsaneInterfaceBlock extends AEBaseEntityBlock<InsaneInterfaceBlockEntity> {

    public InsaneInterfaceBlock() {
        super(metalProps());
    }

    /**
     * 隣のブロックが変わったことを他アドオンへ伝える → {@link AddonIntegrations#notifyNeighborChanged}。
     *
     * <p>AE2 の {@code InterfaceBlock} は自分では隣を見ないが、AppliedFlux などが Mixin で
     * そこに通知を足している。こちらは {@code InterfaceBlock} を継承していないので、同じ通知を自分で出す。</p>
     */
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
            BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (!level.isClientSide()) {
            InsaneInterfaceBlockEntity be = getBlockEntity(level, pos);
            if (be != null) {
                AddonIntegrations.notifyNeighborChanged(be.getInterfaceLogic(), pos, fromPos);
            }
        }
    }

    @Override
    public void onNeighborChange(BlockState state, LevelReader level, BlockPos pos, BlockPos neighbor) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof InsaneInterfaceBlockEntity be) {
            AddonIntegrations.notifyNeighborChanged(be.getInterfaceLogic(), pos, neighbor);
        }
    }

    @Override
    public InteractionResult onActivated(Level level, BlockPos pos, Player player, InteractionHand hand,
            @Nullable ItemStack heldItem, BlockHitResult hit) {
        if (InteractionUtil.isInAlternateUseMode(player)) {
            return InteractionResult.PASS;
        }
        InsaneInterfaceBlockEntity be = getBlockEntity(level, pos);
        if (be == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            be.openMenu(player, MenuLocators.forBlockEntity(be));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
