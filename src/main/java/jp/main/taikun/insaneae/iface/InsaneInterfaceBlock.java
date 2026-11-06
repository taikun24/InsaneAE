package jp.main.taikun.insaneae.iface;

import appeng.block.AEBaseEntityBlock;
import appeng.menu.locator.MenuLocators;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
//? if >=1.21 {
import net.minecraft.network.chat.MutableComponent;
//?}
//? if <1.21 {
/*import appeng.util.InteractionUtil;
import jp.main.taikun.insaneae.integration.AddonIntegrations;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;
*///?}

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
    //? if >=1.21 {
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
    //?} else {
    /*public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
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
    *///?}
        InsaneInterfaceBlockEntity be = getBlockEntity(level, pos);
        if (be == null) {
            //? if >=1.21 {
            return super.useWithoutItem(state, level, pos, player, hitResult);
            //?} else {
            /*return InteractionResult.PASS;
            *///?}
        }
        if (!level.isClientSide()) {
            be.openMenu(player, MenuLocators.forBlockEntity(be));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    /** 1% で別名になるお遊び → {@link InsaneInterfaceNames}。アイテム側もそこを通している。 */
    //? if >=1.21 {
    @Override
    public MutableComponent getName() {
        return InsaneInterfaceNames.of(InsaneInterfaceNames.BLOCK_KEY);
    }
    //?}
}
