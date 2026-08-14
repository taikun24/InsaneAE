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
 * Astral Mekanism の機械が<b>ME インターフェイスへ自動搬出しているとき</b>の受け皿。
 *
 * <p>Mekanism の自動搬出は「1 tick に N mB」「1 スタックずつ」で刻むので、
 * Astral の機械が作る桁 (long 級) には到底追いつかない。
 * 搬出先が ME インターフェイスなら、<b>その場で全量をネットワークへ渡せる</b>ので、
 * 刻まずに 1 回で流し込む ({@link jp.main.taikun.insaneae.mixin.compat.AstralEjectorMixin})。</p>
 *
 * <h2>インターフェイスの設定は尊重する</h2>
 * <ul>
 *   <li><b>設定枠が空 (ストレージとして使っている)</b>: インターフェイスが受け取れるだけ受け取り、
 *       溢れたぶんはネットワークへ直接入れる。「全部ネットワークへ」はこの経路。</li>
 *   <li><b>設定枠がある (在庫確保に使っている)</b>: インターフェイスが受け取るぶんだけ。
 *       設定に無いものを裏からネットワークへ流し込むと、AE2 の見た目の挙動と食い違うため。</li>
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
     * {@code machinePos} の {@code side} 側に ME インターフェイスがあれば受け皿を作る。
     *
     * <p>ブロック版だけでなく<b>ケーブルに付けたインターフェイス (パーツ)</b> も見る。
     * どちらでもなければ null で、呼び出し側は Mekanism 本来の搬出に任せること。</p>
     */
    @Nullable
    public static AstralNetworkEject at(@Nullable Level level, BlockPos machinePos, Direction side) {
        if (level == null || level.isClientSide()) {
            return null;
        }
        BlockPos targetPos = machinePos.relative(side);
        if (!level.isLoaded(targetPos)) {
            // 読み込まれていないチャンクを触らない (Mekanism 側も同じ判断で飛ばしている)。
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
        // 設定枠が入っているインターフェイスは「在庫確保」用。ネットワークへの直接投入はしない。
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
     * <p>まずインターフェイスへ、入り切らなければ (ストレージとして使われている場合だけ)
     * ネットワークへ直接。<b>戻り値のぶんだけ機械側から取り出すこと</b>。</p>
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
