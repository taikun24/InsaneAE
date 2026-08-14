package jp.main.taikun.insaneae.mixin.compat;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.BiPredicate;
import jp.main.taikun.insaneae.config.InsaneAEConfig;
import jp.main.taikun.insaneae.integration.astral.AstralChemicalKey;
import jp.main.taikun.insaneae.integration.astral.AstralNetworkEject;
import mekanism.api.Action;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalTank;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.component.config.slot.ISlotInfo;
import mekanism.common.tile.component.config.slot.InventorySlotInfo;
import mekanism.common.util.ChemicalUtil;
import mekanism.common.util.FluidUtils;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Astral Mekanism &amp; Energistics の自動搬出を、<b>ME インターフェイス相手のときだけ</b>
 * 刻まずにネットワークへ流す。
 *
 * <p>Mekanism の自動搬出には 3 つの上限がある — アイテムは 1 スタック単位、液体は
 * {@code fluidAutoEjectRate}、化学物質は {@code chemicalAutoEjectRate} (どちらも既定 1024/tick)。
 * Astral の機械は 1 回の処理で long 級を作るので、この刻みでは搬出が永遠に追いつかない。
 * 搬出先が ME インターフェイスなら AE2 側が long でまとめて受け取れるため、
 * <b>その面ぶんだけ先に全量を渡し</b>、残りの面は Mekanism 本来の処理へそのまま任せる。</p>
 *
 * <p>受け皿と「どこまで入れてよいか」の判断は {@link AstralNetworkEject} 側にある
 * (設定枠が空のインターフェイスだけネットワークへ直接入れる)。</p>
 *
 * <p>対象は Astral のクラスなのでコンパイル時依存を持たず、{@code @Pseudo} + {@code targets} +
 * {@code remap = false} で名前指定している。Astral が居なければ丸ごと適用されない。
 * 注入はすべて {@code require = 0} なので、Astral 側の作りが変わっても
 * <b>Mekanism 本来の遅い搬出に戻るだけ</b>で壊れない。</p>
 */
@Pseudo
@Mixin(targets = "astral_mekanism.block.blockentity.elements.ExtendedComponentEjector", remap = false)
public abstract class AstralEjectorMixin {

    @Shadow
    private TileEntityMekanism tile;

    @Shadow
    private BiPredicate<IInventorySlot, DataType> canInventorySlotEject;

    /**
     * アイテム: ME インターフェイスへ向いている面のぶんを先に全量渡す。
     *
     * <p>打ち切らない。渡し切れなかったぶんと、インターフェイス以外へ向いている面は
     * Astral 本来のループがそのまま面倒を見る (中身が空になっていれば何もしない)。</p>
     */
    @Inject(method = "outputItems(Lmekanism/common/tile/component/config/ConfigInfo;)V",
            at = @At("HEAD"), require = 0)
    private void insaneae$itemsToNetwork(ConfigInfo info, CallbackInfo ci) {
        if (!InsaneAEConfig.astralNetworkEject()) {
            return;
        }
        Level level = tile.getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }
        for (DataType dataType : info.getSupportedDataTypes()) {
            if (!dataType.canOutput()) {
                continue;
            }
            ISlotInfo slotInfo = info.getSlotInfo(dataType);
            if (!(slotInfo instanceof InventorySlotInfo inventorySlotInfo)) {
                continue;
            }
            for (Direction side : info.getSidesForData(dataType)) {
                AstralNetworkEject sink = AstralNetworkEject.at(level, tile.getBlockPos(), side);
                if (sink == null) {
                    continue;
                }
                for (IInventorySlot slot : inventorySlotInfo.getSlots()) {
                    // 搬出してよいスロットの判定は Astral 本来の経路と同じものを使う。
                    if (canInventorySlotEject != null && !canInventorySlotEject.test(slot, dataType)) {
                        continue;
                    }
                    ItemStack stack = slot.getStack();
                    if (stack.isEmpty()) {
                        continue;
                    }
                    long moved = sink.push(AEItemKey.of(stack), stack.getCount());
                    if (moved > 0) {
                        slot.shrinkStack((int) moved, Action.EXECUTE);
                    }
                }
            }
        }
    }

    /** 液体: ME インターフェイス側は全量、残りの面は Mekanism 本来の {@code fluidAutoEjectRate} 刻み。 */
    @Redirect(method = "eject(Lmekanism/common/lib/transmitter/TransmissionType;"
            + "Lmekanism/common/tile/component/config/ConfigInfo;)V",
            at = @At(value = "INVOKE",
                    target = "Lmekanism/common/util/FluidUtils;emit(Ljava/util/Set;"
                            + "Lmekanism/api/fluid/IExtendedFluidTank;"
                            + "Lnet/minecraft/world/level/block/entity/BlockEntity;I)V"),
            require = 0)
    private void insaneae$fluidToNetwork(Set<Direction> sides, IExtendedFluidTank tank,
            BlockEntity from, int max) {
        Set<Direction> remaining = insaneae$pushSides(sides, from, side -> {
            FluidStack fluid = tank.getFluid();
            if (fluid.isEmpty()) {
                return true;
            }
            return insaneae$drainFluid(tank, fluid, side, from);
        });
        if (!remaining.isEmpty()) {
            FluidUtils.emit(remaining, tank, from, max);
        }
    }

    /** 化学物質: 同上。ネットワークが持てるのは Applied Mekanistics があるときだけ。 */
    @Redirect(method = "eject(Lmekanism/common/lib/transmitter/TransmissionType;"
            + "Lmekanism/common/tile/component/config/ConfigInfo;)V",
            at = @At(value = "INVOKE",
                    target = "Lmekanism/common/util/ChemicalUtil;emit(Ljava/util/Set;"
                            + "Lmekanism/api/chemical/IChemicalTank;"
                            + "Lnet/minecraft/world/level/block/entity/BlockEntity;J)V"),
            require = 0)
    private void insaneae$chemicalToNetwork(Set<Direction> sides, IChemicalTank<?, ?> tank,
            BlockEntity from, long max) {
        Set<Direction> remaining = insaneae$pushSides(sides, from, side -> {
            ChemicalStack<?> stack = tank.getStack();
            AEKey key = AstralChemicalKey.of(stack);
            if (key == null) {
                return false;
            }
            AstralNetworkEject sink = AstralNetworkEject.at(from.getLevel(), from.getBlockPos(), side);
            if (sink == null) {
                return false;
            }
            long moved = sink.push(key, stack.getAmount());
            if (moved > 0) {
                tank.shrinkStack(moved, Action.EXECUTE);
            }
            return true;
        });
        if (!remaining.isEmpty()) {
            ChemicalUtil.emit(remaining, tank, from, max);
        }
    }

    /**
     * ネットワークへ流せた面を取り除いた「残りの面」を返す。
     *
     * @param handler その面を自分で処理できたら true (Mekanism 本来の搬出から外す)
     */
    @Unique
    private Set<Direction> insaneae$pushSides(Set<Direction> sides, BlockEntity from,
            java.util.function.Predicate<Direction> handler) {
        if (!InsaneAEConfig.astralNetworkEject() || from.getLevel() == null
                || from.getLevel().isClientSide()) {
            return sides;
        }
        Set<Direction> remaining = null;
        for (Direction side : sides) {
            if (handler.test(side)) {
                if (remaining == null) {
                    remaining = EnumSet.copyOf(sides);
                }
                remaining.remove(side);
            }
        }
        return remaining == null ? sides : remaining;
    }

    /** 液体 1 面ぶん。インターフェイスが無ければ false を返して Mekanism に任せる。 */
    @Unique
    private boolean insaneae$drainFluid(IExtendedFluidTank tank, FluidStack fluid, Direction side,
            BlockEntity from) {
        AstralNetworkEject sink = AstralNetworkEject.at(from.getLevel(), from.getBlockPos(), side);
        if (sink == null) {
            return false;
        }
        long moved = sink.push(AEFluidKey.of(fluid), fluid.getAmount());
        if (moved > 0) {
            tank.shrinkStack((int) moved, Action.EXECUTE);
        }
        return true;
    }
}
