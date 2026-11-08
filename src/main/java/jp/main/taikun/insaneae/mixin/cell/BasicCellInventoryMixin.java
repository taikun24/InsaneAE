package jp.main.taikun.insaneae.mixin.cell;

import appeng.api.stacks.AEKey;
import appeng.api.storage.cells.IBasicCellItem;
import appeng.api.storage.cells.ISaveProvider;
import appeng.me.cells.BasicCellInventory;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import jp.main.taikun.insaneae.cell.IHugeCellItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import jp.main.taikun.insaneae.util.SaturatingMath;
//? if <1.21 {
/*import jp.main.taikun.insaneae.cell.storage.CellNbt;
*///?}

/**
 * {@link IHugeCellItem} なセルの容量をlongで扱えるようにする。
 *
 * <p>AE2のセル在庫は容量計算をほぼlongで行っているが、次の3か所だけが
 * 2 GiB超・1 EiB超で破綻するのでここで手当てする。</p>
 *
 * <ul>
 *   <li>{@code getTotalBytes()} — {@code cellType.getBytes(stack)}はint返しで、2 GiB上限。</li>
 *   <li>{@code getRemainingItemCount()} — {@code freeBytes * amountPerByte} が
 *       おおよそ1 EiB以上でlongオーバーフローし、負値 → 0になってセルが
 *       「満杯」扱いになり、挿入が一切できなくなる。飽和演算に差し替える。</li>
 *   <li>コンストラクタの {@code maxItemsPerType} — パーティション設定時のみ使われる値で、
 *       同じ乗算でオーバーフローして0になる。0は巨大セルではあり得ないので上限値に直す。</li>
 * </ul>
 *
 * <p>あわせて型、つまりアイテムの種類の数の上限も外す。AE2はコンストラクタで
 * {@code maxItemTypes} を63に丸め、保存済みの型数を {@code short} で持っているので、
 * 上位階層の「数万〜21億種」を扱うにはその2か所も手当てが要る。</p>
 *
 * <p>AE2は自前Modなので難読化されておらず {@code remap = false}。</p>
 */
@Mixin(value = BasicCellInventory.class, remap = false)
public abstract class BasicCellInventoryMixin {

    @Shadow
    @Final
    private IBasicCellItem cellType;

    @Shadow
    @Final
    private ItemStack i;

    @Shadow
    @Final
    @Mutable
    private long maxItemsPerType;

    @Shadow
    private int maxItemTypes;

    /**
     * 読み込み済みの在庫。まだ読んでいなければnull。
     */
    @Shadow
    private Object2LongMap<AEKey> storedAmounts;

    @Shadow
    public abstract long getFreeBytes();

    @Shadow
    public abstract int getUnusedItemCount();

    @Inject(method = "getTotalBytes", at = @At("HEAD"), cancellable = true)
    private void insaneae$hugeTotalBytes(CallbackInfoReturnable<Long> cir) {
        if (cellType instanceof IHugeCellItem huge) {
            cir.setReturnValue(huge.getTotalBytesLong(i));
        }
    }

    @Inject(method = "getRemainingItemCount", at = @At("HEAD"), cancellable = true)
    private void insaneae$saturatingRemainingItemCount(CallbackInfoReturnable<Long> cir) {
        if (!(cellType instanceof IHugeCellItem)) {
            return;
        }
        long amountPerByte = cellType.getKeyType().getAmountPerByte();
        long freeBytes = getFreeBytes();
        // 掛け算だけでなく端数 (getUnusedItemCount) の足し算も溢れうる
        // 溢れて負になると下のmaxで0になり、空いているのに満杯と表示される
        long remaining = SaturatingMath.add(
                SaturatingMath.multiply(freeBytes, amountPerByte), getUnusedItemCount());
        cir.setReturnValue(Math.max(remaining, 0));
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void insaneae$fixMaxItemsPerType(IBasicCellItem cellType, ItemStack o,
                                             ISaveProvider container, CallbackInfo ci) {
        // パーティション設定済みの巨大セルでのみ0になり得る = オーバーフローした場合だけ
        if (cellType instanceof IHugeCellItem && maxItemsPerType <= 0) {
            maxItemsPerType = Long.MAX_VALUE;
        }
        // AE2はコンストラクタで型数を63に丸めるので、巨大セルだけ元の値に戻す
        if (cellType instanceof IHugeCellItem) {
            maxItemTypes = Math.max(1, cellType.getTotalTypes(i));
        }
    }

    /**
     * AE2は保存のたびに種類数を {@code (short) storedAmounts.size()} で持ち直すので
     * 15.xはshortのフィールド、19.2もintのフィールドへshortを経由して入れるので、
     * 32768種を超えると負になり、使用バイト数と空き種類数が狂う。
     */
    @Inject(method = "getStoredItemTypes", at = @At("HEAD"), cancellable = true)
    private void insaneae$storedItemTypesWithoutShortOverflow(CallbackInfoReturnable<Long> cir) {
        if (!(cellType instanceof IHugeCellItem)) {
            return;
        }
        if (storedAmounts != null) {
            cir.setReturnValue((long) storedAmounts.size());
            return;
        }
        //? if <1.21 {
        /*// 中身を保存データに置いているセルはamtsが無いので、アイテムに残した要約の種類数を読む。
        cir.setReturnValue(CellNbt.storedTypes(i));
        *///?}
        // 1.21.1で未読込のときは、コンストラクタがintのまま数えたAE2の値で正しい
    }
}