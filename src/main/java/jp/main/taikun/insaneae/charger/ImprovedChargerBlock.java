package jp.main.taikun.insaneae.charger;

import appeng.block.AEBaseEntityBlock;
import appeng.util.InteractionUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.InteractionHand;
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

    @Override
    public InteractionResult onActivated(Level level, BlockPos pos, Player player, InteractionHand hand,
            @Nullable ItemStack heldItem, BlockHitResult hit) {
        if (InteractionUtil.isInAlternateUseMode(player)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            ImprovedChargerBlockEntity be = getBlockEntity(level, pos);
            if (be != null) {
                be.activate(player);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
