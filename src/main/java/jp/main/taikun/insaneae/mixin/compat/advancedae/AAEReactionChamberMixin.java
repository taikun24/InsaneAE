package jp.main.taikun.insaneae.mixin.compat.advancedae;

import appeng.api.upgrades.IUpgradeableObject;
import jp.main.taikun.insaneae.upgrade.SpeedBoost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Advanced AEのReaction Chamberにも加速カードの倍率を掛ける。
 *
 * <p>速度は {@code tickingRequest} 内のラムダの {@code speedFactor} 局所変数
 * AE2の加速カード枚数のswitchで2/3/5/10/50と決まっている。そこに掛ける。
 * ラムダの番号は1.20.1では {@code $2}、1.21.1では {@code $1} とversionsで違うので注意。</p>
 */
@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.entities.ReactionChamberEntity",
        remap = false, priority = SpeedBoost.MIXIN_PRIORITY)
public abstract class AAEReactionChamberMixin {

    // ラムダのメソッド名はコンパイル結果に依存する = バージョン差で消えやすいのでrequire = 0
    //? if >=1.21 {
    @ModifyVariable(method = "lambda$tickingRequest$1(Lappeng/api/networking/IGrid;)V",
    //?} else {
    /*@ModifyVariable(method = "lambda$tickingRequest$2(Lappeng/api/networking/IGrid;)V",
    *///?}
            at = @At("STORE"), name = "speedFactor", require = 0)
    private int insaneae$boostSpeed(int speed) {
        return SpeedBoost.saturatingMultiply(speed,
                SpeedBoost.multiplier(((IUpgradeableObject) (Object) this).getUpgrades()));
    }
}
