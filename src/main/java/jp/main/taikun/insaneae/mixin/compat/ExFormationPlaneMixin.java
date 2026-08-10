package jp.main.taikun.insaneae.mixin.compat;

import appeng.api.upgrades.IUpgradeableObject;
import jp.main.taikun.insaneae.upgrade.SpeedBoost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ExtendedAE のアクティブ成形プレーンにも加速カードの倍率を掛ける。
 *
 * <p>{@code PartActiveFormationPlane} は AE2 の加速カード枚数から
 * 「1 回に置く個数」({@code getDropMultiplier}) を決めている。その戻り値に掛ける。</p>
 */
@Pseudo
@Mixin(targets = "com.glodblock.github.extendedae.common.parts.PartActiveFormationPlane",
        remap = false, priority = SpeedBoost.MIXIN_PRIORITY)
public abstract class ExFormationPlaneMixin {

    @Inject(method = "getDropMultiplier()J", at = @At("RETURN"), cancellable = true, require = 0)
    private void insaneae$boostDropMultiplier(CallbackInfoReturnable<Long> cir) {
        int multiplier = SpeedBoost.multiplier(((IUpgradeableObject) (Object) this).getUpgrades());
        if (multiplier > 1) {
            cir.setReturnValue(SpeedBoost.saturatingMultiply(cir.getReturnValue(), multiplier));
        }
    }
}
