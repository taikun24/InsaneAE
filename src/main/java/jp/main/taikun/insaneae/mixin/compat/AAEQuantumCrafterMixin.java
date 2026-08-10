package jp.main.taikun.insaneae.mixin.compat;

import appeng.api.upgrades.IUpgradeableObject;
import jp.main.taikun.insaneae.upgrade.SpeedBoost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Advanced AE の Quantum Crafter にも加速カードの倍率を掛ける。
 *
 * <p>速度は {@code tickingRequest} の {@code speedFactor} 局所変数
 * (AE2 の加速カード枚数の switch で 1/8/16/32/64) が決めている。そこに掛ける。</p>
 */
@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.entities.QuantumCrafterEntity",
        remap = false, priority = SpeedBoost.MIXIN_PRIORITY)
public abstract class AAEQuantumCrafterMixin {

    @ModifyVariable(method = "tickingRequest(Lappeng/api/networking/IGridNode;I)"
            + "Lappeng/api/networking/ticking/TickRateModulation;",
            at = @At("STORE"), name = "speedFactor", require = 0)
    private int insaneae$boostSpeed(int speed) {
        return SpeedBoost.saturatingMultiply(speed,
                SpeedBoost.multiplier(((IUpgradeableObject) (Object) this).getUpgrades()));
    }
}
