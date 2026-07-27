package jp.main.taikun.insaneae.mixin;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.blockentity.crafting.MolecularAssemblerBlockEntity;
import jp.main.taikun.insaneae.upgrade.SpeedBoost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 分子組立装置の進捗速度に加速カードの倍率を掛ける。
 *
 * <p>AE2 は {@code progress += userPower(経過tick, 速度値, 電力係数)} で進捗を進めており、
 * 速度値は加速カード枚数の {@code switch} (10/13/17/20/25/…) で決まる。
 * その第 2 引数を倍にするので、消費電力も比例して増える。</p>
 */
@Mixin(value = MolecularAssemblerBlockEntity.class, remap = false,
        priority = SpeedBoost.MIXIN_PRIORITY)
public abstract class MolecularAssemblerBlockEntityMixin {

    @Shadow
    public abstract IUpgradeInventory getUpgrades();

    @ModifyVariable(method = "userPower(IID)I", at = @At("HEAD"), ordinal = 1, argsOnly = true,
            require = 0)
    private int insaneae$boostSpeed(int bonusValue) {
        return SpeedBoost.saturatingMultiply(bonusValue, SpeedBoost.multiplier(getUpgrades()));
    }
}
