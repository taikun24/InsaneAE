package jp.main.taikun.insaneae.iface;

import appeng.block.AEBaseEntityBlock;
import appeng.menu.locator.MenuLocators;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

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

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        InsaneInterfaceBlockEntity be = getBlockEntity(level, pos);
        if (be == null) {
            return super.useWithoutItem(state, level, pos, player, hitResult);
        }
        if (!level.isClientSide()) {
            be.openMenu(player, MenuLocators.forBlockEntity(be));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
