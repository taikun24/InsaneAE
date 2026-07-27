package jp.main.taikun.insaneae.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.helpers.patternprovider.PatternProviderLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * {@link PatternProviderLogic#pushPattern} を独自実装で置き換えたときに、
 * AE2 側のクラフトロック処理 (LOCK_UNTIL_PULSE / LOCK_UNTIL_RESULT) をそのまま呼ぶための入口。
 *
 * <p>{@code onPushPatternSuccess} は private なのでサブクラスからは呼べない。
 * ロック状態そのものも private フィールドなので自前で再実装することもできず、
 * Mixin の {@code @Invoker} で穴を開けている。</p>
 */
@Mixin(value = PatternProviderLogic.class, remap = false)
public interface PatternProviderLogicAccessor {

    @Invoker("onPushPatternSuccess")
    void insaneae$onPushPatternSuccess(IPatternDetails pattern);
}
