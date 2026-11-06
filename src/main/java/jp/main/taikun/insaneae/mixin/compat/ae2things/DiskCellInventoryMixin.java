package jp.main.taikun.insaneae.mixin.compat.ae2things;

import jp.main.taikun.insaneae.integration.ae2things.InsaneDiskItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * AE2 Things の DISK は容量を {@code IDISKCellItem#getBytes} (int) から取るので 2 GiB で止まる。
 * InsaneAE の DISK だけ long の容量を返す。
 *
 * <p>DISK はアイテム 1 個 = 1 バイトで数え、空き容量もこの値からの引き算しかしないので、
 * ここ 1 か所で足りる。</p>
 */
@Pseudo
@Mixin(targets = "io.github.projectet.ae2things.storage.DISKCellInventory", remap = false)
public abstract class DiskCellInventoryMixin {

    @Shadow
    @Final
    private ItemStack i;

    @Inject(method = "getTotalBytes", at = @At("HEAD"), cancellable = true)
    private void insaneae$longCapacity(CallbackInfoReturnable<Long> cir) {
        if (i.getItem() instanceof InsaneDiskItem disk) {
            cir.setReturnValue(disk.capacity());
        }
    }
}
