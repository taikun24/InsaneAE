package jp.main.taikun.insaneae.decoration;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * プレイヤーの人形 (飾り)。見た目はスキンを貼った手書きのブロックモデルだけで、
 * スキンを実行時に取りに行かない (オフラインでも同じ姿で出す)。
 *
 * <p>Block を直接継承しているのは、1.21 の HorizontalDirectionalBlock が
 * codec の実装を要求し、1.20.1 と書き分けが要るため。</p>
 */
public class PlayerDollBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    // 座って脚を正面へ投げ出しているので、正面側だけ箱が長い。
    private static final VoxelShape SHAPE_NORTH = Block.box(4, 0, 2, 12, 13, 12);
    private static final VoxelShape SHAPE_SOUTH = Block.box(4, 0, 4, 12, 13, 14);
    private static final VoxelShape SHAPE_EAST = Block.box(4, 0, 4, 14, 13, 12);
    private static final VoxelShape SHAPE_WEST = Block.box(2, 0, 4, 12, 13, 12);

    public PlayerDollBlock() {
        super(Properties.of().mapColor(MapColor.COLOR_LIGHT_GREEN).sound(SoundType.WOOL)
                .strength(0.5f).noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** 置いた人の方を向かせる。 */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SHAPE_SOUTH;
            case EAST -> SHAPE_EAST;
            case WEST -> SHAPE_WEST;
            default -> SHAPE_NORTH;
        };
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
