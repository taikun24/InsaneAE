package jp.main.taikun.insaneae.mixin;

import appeng.me.cluster.implementations.CraftingCPUCluster;
import jp.main.taikun.insaneae.crafting.BigIntegerCapacityDisplayMarker;
import jp.main.taikun.insaneae.crafting.IBigCraftingCapacity;
import java.math.BigInteger;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** long互換値へ飽和する前のCPU容量を、クライアント表示用に同期する。 */
@Mixin(value = CraftingCPUCluster.class, remap = false, priority = 1500)
public abstract class CraftingCPUNameMixin {
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    @Inject(method = "getName", at = @At("RETURN"), cancellable = true, require = 0)
    private void insaneae$syncExactCapacity(CallbackInfoReturnable<Component> cir) {
        Object target = this;
        // InsaneAEのMixinが適用されていない環境では、CPU名を変更しない。
        if (!(target instanceof IBigCraftingCapacity capacity)) {
            return;
        }

        BigInteger exact = capacity.insaneae$exactStorageCapacity();
        // long内のCPUは既存のAE2表示を維持し、超過したCPUだけマーカーを付ける。
        if (exact.compareTo(LONG_MAX) <= 0) {
            return;
        }

        Component name = cir.getReturnValue();
        // 未命名CPUには表示名を付けず、クライアントでAE2標準の連番名を復元できるようにする。
        if (name == null) {
            name = Component.empty();
        }
        cir.setReturnValue(BigIntegerCapacityDisplayMarker.mark(name, exact));
    }
}
