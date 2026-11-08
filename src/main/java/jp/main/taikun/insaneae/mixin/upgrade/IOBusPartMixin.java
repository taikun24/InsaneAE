package jp.main.taikun.insaneae.mixin.upgrade;

import appeng.api.upgrades.IUpgradeableObject;
import appeng.parts.automation.IOBusPart;
import jp.main.taikun.insaneae.upgrade.SpeedBoost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * インポート／エクスポートバスの1 tickあたり処理数に加速カードの倍率を掛ける。
 *
 * <p>AE2側は{@code switch}で、加速カード枚数に対して1/8/32/64/96を返しており、
 * 表の範囲外、5枚以上の場合は最低速の1に落ちてしまう。よって枚数を盛るのではなく
 * 結果に倍率を掛ける。</p>
 */
@Mixin(value = IOBusPart.class, remap = false, priority = SpeedBoost.MIXIN_PRIORITY)
public abstract class IOBusPartMixin {

    @Inject(method = "getOperationsPerTick", at = @At("RETURN"), cancellable = true, require = 0)
    private void insaneae$boostOperations(CallbackInfoReturnable<Integer> cir) {
        // getUpgrades() は親クラス (UpgradeablePart) 定義なので @Shadowでは拾えない
        int multiplier = SpeedBoost.multiplier(((IUpgradeableObject) (Object) this).getUpgrades());
        if (multiplier > 1) {
            cir.setReturnValue(SpeedBoost.saturatingMultiply(cir.getReturnValue(), multiplier));
        }
    }
}
