package jp.main.taikun.insaneae.network;

import appeng.blockentity.networking.ControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 超次元 ME コントローラ。
 *
 * <p><b>コントローラとしての挙動は AE2 のものそのまま</b>で、独自のロジックは持たない。
 * 追加しているのは<b>型だけ</b>で、その型が居ることが
 * {@link HyperNetwork#isUnlocked} の解錠条件になっている
 * (＝{@link HyperCablePart} が 32 本を超えられるようになる)。</p>
 *
 * <p>AE2 の {@code PathingService} はコントローラを
 * {@code instanceof ControllerBlockEntity} で数えているので、これを継承していれば
 * <b>普通のコントローラとして機能し、AE2 のコントローラと隣接して 1 つの構造も組める</b>
 * (同じ 7x7x7 の制限・衝突判定がそのまま効く)。</p>
 *
 * <p>コントローラ自身のノードは {@code CANNOT_CARRY} でチャンネル上限が 0 —
 * 「出す側」であって運ばないので、<b>ここを強化しても本数は増えない</b>。
 * 増やせるのはケーブルだけ、という AE2 の作りについては {@link HyperNetwork} を参照。</p>
 */
public class HyperControllerBlockEntity extends ControllerBlockEntity {

    public HyperControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }
}
