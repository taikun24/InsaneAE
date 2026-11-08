package jp.main.taikun.insaneae.mixin.compat.extendedae;

import jp.main.taikun.insaneae.upgrade.SpeedBoost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * ExtendedAEの拡張刻印機 (Ex Inscriber) にも加速カードの倍率を掛ける。
 *
 * <p>速度は各 {@code InscriberThread} がラムダの中でAE2の加速カード枚数から決めている
 * AE2の刻印機と同じ{@code speedFactor}局所変数。AE2用の
 * {@code InscriberBlockEntityMixin} と同じ手で書き換える。</p>
 *
 * <p>{@code host}フィールドの型はExtendedAEのクラスで、コンパイル時に参照できないので、
 * {@code @Shadow}ではなく、リフレクションの{@link SpeedBoost#multiplierFromHost}で
 * 機械本体のカードを見る。</p>
 */
@Pseudo
@Mixin(targets = "com.glodblock.github.extendedae.common.me.InscriberThread",
        remap = false, priority = SpeedBoost.MIXIN_PRIORITY)
public abstract class ExInscriberThreadMixin {

    // ラムダのメソッド名はコンパイル結果に依存する = バージョン差で消えやすいのでrequire = 0
    @ModifyVariable(method = "lambda$tick$1(Lappeng/api/networking/IGrid;)V",
            at = @At("STORE"), name = "speedFactor", require = 0)
    private int insaneae$boostSpeed(int speed) {
        return SpeedBoost.saturatingMultiply(speed, SpeedBoost.multiplierFromHost(this));
    }
}
