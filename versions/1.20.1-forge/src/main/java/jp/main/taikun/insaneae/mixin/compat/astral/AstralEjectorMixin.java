package jp.main.taikun.insaneae.mixin.compat.astral;

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
import java.util.function.Predicate;

/**
 * Astral Mekanism &amp; Energisticsの自動搬出を、MEインターフェイス相手のときだけ
 * 刻まずにネットワークへ流す。
 *
 * <p>Mekanismの自動搬出には3つの上限がある — アイテムは1スタック単位、液体は
 * {@code fluidAutoEjectRate}、化学物質は{@code chemicalAutoEjectRate}。どちらも既定は1024/tick。
 * Astralの機械は1回の処理でlong級を作るので、この刻みでは搬出が永遠に追いつかない。
 * 搬出先がMEインターフェイスならAE2側がlongでまとめて受け取れるため、
 * その面ぶんだけ先に全量を渡し、残りの面はMekanism本来の処理へそのまま任せる。</p>
 *
 * <p>受け皿と「どこまで入れてよいか」の判断は {@link AstralNetworkEject} 側にある
 * 設定枠が空のインターフェイスだけ、ネットワークへ直接入れる。</p>
 *
 * <p>対象はAstralのクラスなのでコンパイル時依存を持たず、{@code @Pseudo} + {@code targets} +
 * {@code remap = false} で名前指定している。Astralが居なければ丸ごと適用されない。
 * 注入はすべて {@code require = 0} なので、Astral側の作りが変わっても
 * Mekanism本来の遅い搬出に戻るだけで壊れない。</p>
 */
@Pseudo
@Mixin(targets = "astral_mekanism.block.blockentity.elements.ExtendedComponentEjector", remap = false)
public abstract class AstralEjectorMixin {

    @Shadow
    private TileEntityMekanism tile;

    @Shadow
    private BiPredicate<IInventorySlot, DataType> canInventorySlotEject;

    /**
     * アイテム: MEインターフェイスへ向いている面のぶんを先に全量渡す。
     *
     * <p>打ち切らない。渡し切れなかったぶんと、インターフェイス以外へ向いている面は
     * Astral本来のループがそのまま面倒を見る。中身が空になっていれば何もしない。</p>
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
                    // 搬出してよいスロットの判定はAstral本来の経路と同じものを使う
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

    /** 液体: MEインターフェイス側は全量、残りの面はMekanism本来の {@code fluidAutoEjectRate} 刻み。 */
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

    /** 化学物質: 同上。ネットワークが持てるのはApplied Mekanisticsがあるときだけ。 */
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
     * @param handlerその面を自分で処理できたらtrue。Mekanism本来の搬出から外す
     */
    @Unique
    private Set<Direction> insaneae$pushSides(Set<Direction> sides, BlockEntity from,
            Predicate<Direction> handler) {
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

    /** 液体1面ぶん。インターフェイスが無ければfalseを返してMekanismに任せる。 */
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
