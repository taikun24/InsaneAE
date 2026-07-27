package jp.main.taikun.insaneae.mixin;

import appeng.api.upgrades.IUpgradeableObject;
import appeng.parts.automation.IOBusPart;
import jp.main.taikun.insaneae.upgrade.SpeedBoost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * インポート／エクスポートバスの 1 tick あたり処理数に加速カードの倍率を掛ける。
 *
 * <p>AE2 側は {@code switch (加速カード枚数)} で 1/8/32/64/96 を返しており、
 * 表の範囲外 (5 枚以上) は最低速の 1 に落ちてしまう。よって枚数を盛るのではなく
 * 結果に倍率を掛ける。</p>
 */
@Mixin(value = IOBusPart.class, remap = false, priority = SpeedBoost.MIXIN_PRIORITY)
public abstract class IOBusPartMixin {

    @Inject(method = "getOperationsPerTick", at = @At("RETURN"), cancellable = true, require = 0)
    private void insaneae$boostOperations(CallbackInfoReturnable<Integer> cir) {
        // getUpgrades() は親クラス (UpgradeablePart) 定義なので @Shadow では拾えない。
        int multiplier = SpeedBoost.multiplier(((IUpgradeableObject) (Object) this).getUpgrades());
        if (multiplier > 1) {
            cir.setReturnValue(SpeedBoost.saturatingMultiply(cir.getReturnValue(), multiplier));
        }
    }
}
