package jp.main.taikun.insaneae.mixin;

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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@link IHugeCellItem} なセルの容量を long で扱えるようにする。
 *
 * <p>AE2 のセル在庫は容量計算をほぼ long で行っているが、次の 3 か所だけが
 * 2 GiB 超・1 EiB 超で破綻するのでここで手当てする。</p>
 *
 * <ul>
 *   <li>{@code getTotalBytes()} — {@code cellType.getBytes(stack)} は int 返し (2 GiB 上限)。</li>
 *   <li>{@code getRemainingItemCount()} — {@code freeBytes * amountPerByte} が
 *       おおよそ 1 EiB 以上で long オーバーフローし、負値 → 0 になってセルが
 *       「満杯」扱いになる (挿入が一切できなくなる)。飽和演算に差し替える。</li>
 *   <li>コンストラクタの {@code maxItemsPerType} — パーティション設定時のみ使われる値で、
 *       同じ乗算でオーバーフローして 0 になる。0 は巨大セルではあり得ないので上限値に直す。</li>
 * </ul>
 *
 * <p>あわせて<b>型 (アイテムの種類) の数</b>の上限も外す。AE2 はコンストラクタで
 * {@code maxItemTypes} を 63 に丸め、保存済みの型数を {@code short} で持っているので、
 * 上位階層の「数万〜21 億種」を扱うにはその 2 か所も手当てが要る。</p>
 *
 * <p>AE2 は自前 Mod なので難読化されておらず {@code remap = false}。</p>
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
     * 読み込み済みの在庫。まだ読んでいなければ null。
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
        long remaining = freeBytes > Long.MAX_VALUE / amountPerByte
                ? Long.MAX_VALUE
                : freeBytes * amountPerByte + getUnusedItemCount();
        cir.setReturnValue(Math.max(remaining, 0));
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void insaneae$fixMaxItemsPerType(IBasicCellItem cellType, ItemStack o,
                                             ISaveProvider container, CallbackInfo ci) {
        // パーティション設定済みの巨大セルでのみ 0 になり得る = オーバーフローした場合だけ。
        if (cellType instanceof IHugeCellItem && maxItemsPerType <= 0) {
            maxItemsPerType = Long.MAX_VALUE;
        }
        // AE2 はコンストラクタで型数を 63 に丸めるので、巨大セルだけ元の値に戻す。
        if (cellType instanceof IHugeCellItem) {
            maxItemTypes = Math.max(1, cellType.getTotalTypes(i));
        }
    }

    // 1.20.1 (AE2 15.2.16) にはここに getStoredItemTypes への注入があった。
    // 当時 AE2 の storedItems が short だったため 32767 種で桁あふれして
    // 使用バイト数と残り型数が壊れていたのを、NBT のキー配列長から数え直していた。
    // AE2 19.2 で storedItems は int になり、値も NBT 直読みではなく
    // getStoredStacks().size() から入るようになったので、この回避策は不要になった。
}