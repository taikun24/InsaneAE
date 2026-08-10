package jp.main.taikun.insaneae.iface;

import appeng.block.AEBaseEntityBlock;
import appeng.menu.locator.MenuLocators;
import appeng.util.InteractionUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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
