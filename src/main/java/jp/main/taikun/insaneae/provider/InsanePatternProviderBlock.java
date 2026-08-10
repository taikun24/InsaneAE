package jp.main.taikun.insaneae.provider;

import appeng.block.AEBaseEntityBlock;
import appeng.menu.locator.MenuLocators;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        InsanePatternProviderBlockEntity be = getBlockEntity(level, pos);
        if (be == null) {
            return super.useWithoutItem(state, level, pos, player, hitResult);
        }
        if (!level.isClientSide()) {
            be.openMenu(player, MenuLocators.forBlockEntity(be));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
