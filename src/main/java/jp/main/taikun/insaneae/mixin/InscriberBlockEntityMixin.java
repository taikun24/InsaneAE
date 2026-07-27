package jp.main.taikun.insaneae.mixin;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.blockentity.misc.InscriberBlockEntity;
import jp.main.taikun.insaneae.upgrade.SpeedBoost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 刻印機の加工速度に加速カードの倍率を掛ける。
 *
 * <p>AE2 は加速カード枚数の {@code switch} で速度値 (2/3/5/10/50) を決め、
 * その値ぶん進捗を進める (消費電力も速度値に比例)。ラムダ内の局所変数を書き換える。</p>
 */
@Mixin(value = InscriberBlockEntity.class, remap = false, priority = SpeedBoost.MIXIN_PRIORITY)
public abstract class InscriberBlockEntityMixin {

    @Shadow
    public abstract IUpgradeInventory getUpgrades();

    // ラムダのメソッド名は AE2 のコンパイル結果に依存する = バージョン差で消えやすいので require = 0。
    @ModifyVariable(method = "lambda$tickingRequest$1(Lappeng/api/networking/IGrid;)V",
            at = @At("STORE"), name = "speedFactor", require = 0)
    private int insaneae$boostSpeed(int speed) {
        return SpeedBoost.saturatingMultiply(speed, SpeedBoost.multiplier(getUpgrades()));
    }
}
