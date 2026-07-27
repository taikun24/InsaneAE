package jp.main.taikun.insaneae.mixin;

import appeng.core.localization.Tooltips;
import jp.main.taikun.insaneae.crafting.HugeByteAmounts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * AE2 の {@code Tooltips.getByteAmount} が 1000 GiB 以上で
 * {@code ArrayIndexOutOfBoundsException} を投げる問題への対処。
 *
 * <p>クラフト CPU 一覧のツールチップ ({@code CPUSelectionList.getTooltip} → {@code ofBytes})
 * などが呼ぶため、1T 以上の CPU にカーソルを合わせるとゲームがクラッシュしていた。
 * 詳細は {@link HugeByteAmounts} を参照。</p>
 *
 * <p>AE2 は自前 Mod なので難読化されておらず {@code remap = false}。</p>
 */
@Mixin(value = Tooltips.class, remap = false)
public class TooltipsMixin {

    @Inject(method = "getByteAmount(J)Lappeng/core/localization/Tooltips$Amount;",
            at = @At("HEAD"), cancellable = true)
    private static void insaneae$formatHugeByteAmounts(long bytes,
            CallbackInfoReturnable<Tooltips.Amount> cir) {
        // AE2 が正しく処理できる範囲はそのまま任せる (表示を変えないため)。
        if (bytes >= HugeByteAmounts.AE2_LIMIT) {
            cir.setReturnValue(HugeByteAmounts.format(bytes));
        }
    }
}
