package jp.main.taikun.insaneae.charger;

import appeng.block.AEBaseEntityBlock;
import appeng.util.InteractionUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Improved Crystal Charger のブロック。中身は {@link ImprovedChargerBlockEntity}。
 *
 * <p>AE2 のチャージャーと同じく GUI は持たず、右クリックで出し入れする。
 * 自動化する場合は入出力とも Forge のアイテムハンドラ (ホッパー / インポートバス等) 経由。</p>
 */
public class ImprovedChargerBlock extends AEBaseEntityBlock<ImprovedChargerBlockEntity> {

    public ImprovedChargerBlock() {
        // noOcclusion: フルキューブでないモデルなので、隣のブロックの面を消させない。
        // 付けないと隣接面がカリングされ、開いている部分から地形の裏が見える (AE2 のチャージャーと同じ対策)。
        super(metalProps().noOcclusion());
    }

    /**
     * 光の遮り方。AE2 のチャージャーに合わせて 2 だけ減衰させる
     * ({@code noOcclusion()} の既定は「光を全く遮らない」なので、そのままだと素通りする)。
     */
    @Override
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 2;
    }

    // 1.20.5 でブロックの右クリックは「手に持っている場合 (useItemOn)」と
    // 「素手の場合 (useWithoutItem)」に分割され、AE2 の onActivated も無くなった。
    // どちらも同じ「入れる／取り出す」を行うので、実処理は activate() に寄せてある。

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
        activate(level, pos, player);
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (InteractionUtil.isInAlternateUseMode(player)) {
            return InteractionResult.PASS;
        }
        activate(level, pos, player);
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    private void activate(Level level, BlockPos pos, Player player) {
        if (!level.isClientSide()) {
            ImprovedChargerBlockEntity be = getBlockEntity(level, pos);
            if (be != null) {
                be.activate(player);
            }
        }
    }
}
