package jp.main.taikun.insaneae.charger;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.api.orientation.RelativeSide;
import appeng.api.util.AEAxisAlignedBB;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Improved Crystal Charger のブロック。中身は {@link ImprovedChargerBlockEntity}。
 *
 * <p>AE2 のチャージャーと同じく GUI は持たず、右クリックで出し入れする。
 * 自動化する場合は入出力とも Forge のアイテムハンドラ (ホッパー / インポートバス等) 経由。</p>
 *
 * <p>向き・当たり判定も AE2 のチャージャーに合わせてある
 * ({@link #getOrientationStrategy()} / {@link #getShape}) — 見た目が同じ形なので、
 * 判定だけフルキューブだと「開いている所に当たる」ことになるため。</p>
 */
public class ImprovedChargerBlock extends AEBaseEntityBlock<ImprovedChargerBlockEntity> {

    public ImprovedChargerBlock() {
        // noOcclusion: フルキューブでないモデルなので、隣のブロックの面を消させない。
        // 付けないと隣接面がカリングされ、開いている部分から地形の裏が見える (AE2 のチャージャーと同じ対策)。
        super(metalProps().noOcclusion());
    }

    /**
     * 置いた向き (と回転) を持たせる。AE2 のチャージャーと同じ {@code full}:
     * 6 方向 × 4 回転 = 24 通りを {@code facing} / {@code spin} のブロックステートで持つ。
     *
     * <p>ここを変えるとブロックステートの一覧も変わるので、
     * {@code ModBlockStateProvider} 側の生成 (24 通り) と必ず揃えること。</p>
     */
    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.full();
    }

    /**
     * 見た目どおりの当たり判定。AE2 のチャージャーと同じ組み立て方で、
     * 中央の柱 (各面 2px 内側) を<b>上下方向は端まで</b>伸ばし、
     * さらに<b>正面側</b>だけ端まで伸ばした箱を返す。
     *
     * <p>向きは {@code facing} / {@code spin} から引くので、置き方を変えると判定も一緒に回る。</p>
     */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        var orientation = getOrientation(state);
        var up = orientation.getSide(RelativeSide.TOP);
        var forward = orientation.getSide(RelativeSide.FRONT);
        var twoPixels = 2.0 / 16.0;

        var bb = new AEAxisAlignedBB(twoPixels, twoPixels, twoPixels,
                1.0 - twoPixels, 1.0 - twoPixels, 1.0 - twoPixels);

        if (up.getStepX() != 0) {
            bb.minX = 0;
            bb.maxX = 1;
        }
        if (up.getStepY() != 0) {
            bb.minY = 0;
            bb.maxY = 1;
        }
        if (up.getStepZ() != 0) {
            bb.minZ = 0;
            bb.maxZ = 1;
        }

        switch (forward) {
            case DOWN -> bb.maxY = 1;
            case UP -> bb.minY = 0;
            case NORTH -> bb.maxZ = 1;
            case SOUTH -> bb.minZ = 0;
            case EAST -> bb.minX = 0;
            case WEST -> bb.maxX = 1;
            default -> {
            }
        }

        return Shapes.create(bb.getBoundingBox());
    }

    /**
     * 当たり判定 (移動用) はフルキューブ。AE2 のチャージャーと同じで、
     * 見た目の隙間にプレイヤーがめり込まないようにするため。
     */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return Shapes.create(new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0));
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
