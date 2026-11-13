package jp.main.taikun.insaneae.mixin.cell;

import appeng.api.stacks.AEKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * {@code KeyCounter} の足し算がlongの天井を越えたら、負へ折り返さずに天井で止める (issue #22)。
 *
 * <p>ネットワークの在庫はドライブの全セルが同じ {@code KeyCounter} に {@code add} して作る。
 * 1個でlongの天井近くまで入るセル (InsaneAEの大容量セル・FEセル・DISK、AppliedFluxのFEセル) が
 * 2個並ぶと合計が負になり、ターミナルやストレージモニターから消える。足す側のセルを全部直すのは
 * 他Modのセルまで追いきれないので、合流先の1点で止める。</p>
 *
 * <p>経路は {@code KeyCounter#add} → {@code VariantCounter#add} の1本で、{@code addAll} も
 * ここを通る。CallbackInfoを作らない {@code ModifyVariable} にしてあるのは、在庫の作り直しの
 * たびに全キーぶん呼ばれるため。</p>
 */
@Mixin(targets = "appeng.api.stacks.VariantCounter", remap = false)
public abstract class VariantCounterSaturationMixin {

    @Shadow
    public abstract long get(AEKey key);

    @ModifyVariable(method = "add", at = @At("HEAD"), argsOnly = true)
    private long insaneae$saturate(long amount, AEKey key, long original) {
        if (amount <= 0L) {
            return amount;
        }
        long current = get(key);
        long headroom = Long.MAX_VALUE - Math.max(current, 0L);
        return Math.min(amount, headroom);
    }
}
