package jp.main.taikun.insaneae.mixin.compat.ae2things;

import jp.main.taikun.insaneae.integration.ae2things.InsaneDisk;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * AE2 ThingsのDISKは容量を {@code IDISKCellItem#getBytes} (int) から取るので2 GiBで止まる。
 * InsaneAEのDISKだけlongの容量を返す。AE2 MEGA Thingsの液体・化学物質DISKも
 * {@code DISKCellInventory} の子クラスで動くので、ここで一緒に広がる。
 *
 * <p>空き容量はこの値からの引き算しかしないので、
 * ここ1か所で足りる。</p>
 */
@Pseudo
@Mixin(targets = "io.github.projectet.ae2things.storage.DISKCellInventory", remap = false)
public abstract class DiskCellInventoryMixin {

    @Shadow
    @Final
    private ItemStack i;

    @Inject(method = "getTotalBytes", at = @At("HEAD"), cancellable = true)
    private void insaneae$longCapacity(CallbackInfoReturnable<Long> cir) {
        if (i.getItem() instanceof InsaneDisk disk) {
            cir.setReturnValue(disk.capacity());
        }
    }
}
