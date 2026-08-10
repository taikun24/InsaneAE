package jp.main.taikun.insaneae.provider;

import appeng.block.AEBaseEntityBlock;
import appeng.menu.locator.MenuLocators;
import appeng.util.InteractionUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 特大パターンプロバイダーのブロック。中身は {@link InsanePatternProviderBlockEntity}。
 *
 * <p>AE2 のパターンプロバイダと違って向き (押し出し方向) を持たない。
 * 右クリックで画面を開くだけ。</p>
 */
public class InsanePatternProviderBlock extends AEBaseEntityBlock<InsanePatternProviderBlockEntity> {

    public InsanePatternProviderBlock() {
        super(metalProps());
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
            BlockPos fromPos, boolean isMoving) {
        // レッドストーンでのクラフトロック用。
        InsanePatternProviderBlockEntity be = getBlockEntity(level, pos);
        if (be != null) {
            be.getLogic().updateRedstoneState();
        }
    }

    @Override
    public InteractionResult onActivated(Level level, BlockPos pos, Player player, InteractionHand hand,
            @Nullable ItemStack heldItem, BlockHitResult hit) {
        if (InteractionUtil.isInAlternateUseMode(player)) {
            return InteractionResult.PASS;
        }
        InsanePatternProviderBlockEntity be = getBlockEntity(level, pos);
        if (be == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            be.openMenu(player, MenuLocators.forBlockEntity(be));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
