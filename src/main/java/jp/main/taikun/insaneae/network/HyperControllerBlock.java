package jp.main.taikun.insaneae.network;

import appeng.block.networking.ControllerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 超次元 ME コントローラのブロック。
 *
 * <p>{@link ControllerBlock} を継承しているので、ブロックステートの
 * {@code state} (offline / online / conflicted) と {@code type} (見た目の種類) を
 * そのまま引き継ぐ。{@link HyperControllerBlockEntity} が
 * {@code ControllerBlock.CONTROLLER_STATE} を直接書き換えるため、
 * <b>この 2 つのプロパティは消せない</b>。</p>
 *
 * <h2>見た目は常に単体ブロック ({@code type=block})</h2>
 * <p>AE2 の {@code ControllerBlock} は隣接の並び方を見て柱 / 内部の絵に切り替えるが、
 * その判定 ({@code isController}) が <b>{@code ae2:controller} ブロックとの一致</b>なので
 * 派生ブロックでは常に false になる。中途半端に AE2 のコントローラだけを見て
 * 柱になったり単体になったりするより、<b>常に単体の絵に固定</b>した方が素直なので、
 * 置いたときも隣が変わったときも {@code type} を触らないようにしてある。
 * ブロックステート JSON も {@code type=block} の 3 状態ぶんしか出していない。</p>
 */
public class HyperControllerBlock extends ControllerBlock {

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // super は隣接を見て type を決めようとするので使わない。
        // 既定値 (offline / block) のまま置き、状態は BlockEntity が更新する。
        return defaultBlockState();
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        // 隣が変わっても見た目は変えない (type は block 固定)。
        return state;
    }
}
