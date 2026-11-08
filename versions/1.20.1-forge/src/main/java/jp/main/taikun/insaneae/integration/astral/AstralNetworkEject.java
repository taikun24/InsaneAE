package jp.main.taikun.insaneae.integration.astral;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPartHost;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.helpers.InterfaceLogic;
import appeng.helpers.InterfaceLogicHost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Astral Mekanismの機械がMEインターフェイスへ自動搬出しているときの受け皿。
 *
 * <p>Mekanismの自動搬出は「1 tickにN mB」「1スタックずつ」で刻むので、
 * Astralの機械が作る桁、つまりlong級には到底追いつかない。
 * 搬出先がMEインターフェイスなら、その場で全量をネットワークへ渡せるので、
 * 刻まずに1回で流し込む ({@link jp.main.taikun.insaneae.mixin.compat.astral.AstralEjectorMixin})。</p>
 *
 * <h2>インターフェイスの設定は尊重する</h2>
 * <ul>
 *   <li>設定枠が空。ストレージとして使っている場合は、インターフェイスが受け取れるだけ受け取り、
 *       溢れたぶんはネットワークへ直接入れる。「全部ネットワークへ」はこの経路。</li>
 *   <li>設定枠がある。在庫確保に使っている場合は、インターフェイスが受け取るぶんだけ。
 *       設定に無いものを裏からネットワークへ流し込むと、AE2の見た目の挙動と食い違うため。</li>
 * </ul>
 */
public final class AstralNetworkEject {

    private final MEStorage interfaceInventory;
    @Nullable
    private final MEStorage networkInventory;
    private final IActionSource source;

    private AstralNetworkEject(MEStorage interfaceInventory, @Nullable MEStorage networkInventory) {
        this.interfaceInventory = interfaceInventory;
        this.networkInventory = networkInventory;
        this.source = IActionSource.empty();
    }

    /**
     * {@code machinePos} の {@code side} 側にMEインターフェイスがあれば受け皿を作る。
     *
     * <p>ブロック版だけでなく、ケーブルに付けたインターフェイス、つまりパーツも見る。
     * どちらでもなければnullで、呼び出し側はMekanism本来の搬出に任せること。</p>
     */
    @Nullable
    public static AstralNetworkEject at(@Nullable Level level, BlockPos machinePos, Direction side) {
        if (level == null || level.isClientSide()) {
            return null;
        }
        BlockPos targetPos = machinePos.relative(side);
        if (!level.isLoaded(targetPos)) {
            // 読み込まれていないチャンクを触らない。Mekanism側も同じ判断で飛ばしている
            return null;
        }
        BlockEntity target = level.getBlockEntity(targetPos);
        if (target == null) {
            return null;
        }
        Direction facingMachine = side.getOpposite();

        InterfaceLogicHost host = null;
        if (target instanceof InterfaceLogicHost blockInterface) {
            host = blockInterface;
        } else if (target instanceof IPartHost partHost
                && partHost.getPart(facingMachine) instanceof InterfaceLogicHost partInterface) {
            host = partInterface;
        }
        if (host == null) {
            return null;
        }

        InterfaceLogic logic = host.getInterfaceLogic();
        if (logic == null) {
            return null;
        }
        // 設定枠が入っているインターフェイスは「在庫確保」用。ネットワークへの直接投入はしない
        MEStorage network = logic.getConfig().isEmpty() ? networkOf(target, facingMachine) : null;
        return new AstralNetworkEject(logic.getInventory(), network);
    }

    @Nullable
    private static MEStorage networkOf(BlockEntity target, Direction side) {
        if (!(target instanceof IInWorldGridNodeHost nodeHost)) {
            return null;
        }
        IGridNode node = nodeHost.getGridNode(side);
        if (node == null) {
            return null;
        }
        IGrid grid = node.getGrid();
        return grid == null ? null : grid.getStorageService().getInventory();
    }

    /**
     * 入るだけ入れて、実際に入った量を返す。
     *
     * <p>まずインターフェイスへ、入り切らなければ。ストレージとして使われている場合だけ
     * ネットワークへ直接。戻り値のぶんだけ機械側から取り出すこと。</p>
     */
    public long push(AEKey key, long amount) {
        if (key == null || amount <= 0) {
            return 0;
        }
        long moved = interfaceInventory.insert(key, amount, Actionable.MODULATE, source);
        if (moved < amount && networkInventory != null) {
            moved += networkInventory.insert(key, amount - moved, Actionable.MODULATE, source);
        }
        return moved;
    }
}
