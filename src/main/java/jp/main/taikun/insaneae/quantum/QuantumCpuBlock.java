package jp.main.taikun.insaneae.quantum;

import appeng.block.AEBaseEntityBlock;
import appeng.menu.locator.MenuLocators;
import appeng.util.InteractionUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Quantum CPU のブロック。中身は {@link QuantumCpuBlockEntity}。
 *
 * <p>パターンプロバイダと違って押し出し方向を持たない (自分で組み立てるため)。</p>
 */
public class QuantumCpuBlock extends AEBaseEntityBlock<QuantumCpuBlockEntity> {

    public QuantumCpuBlock() {
        // noOcclusion: モデルに透明な部分があるので、隣のブロックの面を消させない。
        // 付けないと隣接面がカリングされ、透けた部分から地形の裏 (=何も無い空間) が見える。
        super(metalProps().noOcclusion());
    }

    /**
     * 光の遮り方。AE2 のチャージャーと同じく完全遮光にはしない。
     *
     * <p>{@code noOcclusion()} だけだと既定で「光を全く遮らない」扱いになり、
     * 中身の詰まったブロックなのに光が通り抜けてしまうので、2 だけ減衰させる。</p>
     */
    @Override
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 2;
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
            BlockPos fromPos, boolean isMoving) {
        QuantumCpuBlockEntity be = getBlockEntity(level, pos);
        if (be != null) {
            be.getLogic().updateRedstoneState();
        }
    }

    // 1.20.5 でブロックの右クリックは「手に持っている場合 (useItemOn)」と
    // 「素手の場合 (useWithoutItem)」に分割され、AE2 の onActivated も無くなった。
    // どちらも画面を開くだけなので、実処理は openScreen() に寄せてある。

    @Override
    protected ItemInteractionResult useItemOn(ItemStack heldItem, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (InteractionUtil.isInAlternateUseMode(player)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        // メモリーカード等、AE2 側が処理するものを先に通す。
        var handled = super.useItemOn(heldItem, state, level, pos, player, hand, hit);
        if (handled != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
            return handled;
        }
        return openScreen(level, pos, player)
                ? ItemInteractionResult.sidedSuccess(level.isClientSide())
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (InteractionUtil.isInAlternateUseMode(player)) {
            return InteractionResult.PASS;
        }
        return openScreen(level, pos, player)
                ? InteractionResult.sidedSuccess(level.isClientSide())
                : InteractionResult.PASS;
    }

    private boolean openScreen(Level level, BlockPos pos, Player player) {
        QuantumCpuBlockEntity be = getBlockEntity(level, pos);
        if (be == null) {
            return false;
        }
        if (!level.isClientSide()) {
            be.openMenu(player, MenuLocators.forBlockEntity(be));
        }
        return true;
    }
}
