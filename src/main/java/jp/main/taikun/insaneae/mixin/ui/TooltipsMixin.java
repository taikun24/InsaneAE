package jp.main.taikun.insaneae.mixin.ui;

import appeng.core.localization.Tooltips;
import jp.main.taikun.insaneae.crafting.HugeByteAmounts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * AE2の {@code Tooltips.getByteAmount} が1000 GiB以上で
 * {@code ArrayIndexOutOfBoundsException} を投げる問題への対処。
 *
 * <p>クラフトCPU一覧のツールチップ。{@code CPUSelectionList.getTooltip}から{@code ofBytes}へ
 * などが呼ぶため、1T以上のCPUにカーソルを合わせるとゲームがクラッシュする。
 * 詳細は {@link HugeByteAmounts} を参照。</p>
 *
 * <p>AE2は自前Modなので難読化されておらず {@code remap = false}。</p>
 */
@Mixin(value = Tooltips.class, remap = false)
public class TooltipsMixin {

    @Inject(method = "getByteAmount(J)Lappeng/core/localization/Tooltips$Amount;",
            at = @At("HEAD"), cancellable = true)
    private static void insaneae$formatHugeByteAmounts(long bytes,
            CallbackInfoReturnable<Tooltips.Amount> cir) {
        // AE2が正しく処理できる範囲はそのまま任せる。表示を変えないため
        if (bytes >= HugeByteAmounts.AE2_LIMIT) {
            cir.setReturnValue(HugeByteAmounts.format(bytes));
        }
    }
}
