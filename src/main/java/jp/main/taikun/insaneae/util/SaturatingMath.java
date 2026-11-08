package jp.main.taikun.insaneae.util;

import java.math.BigInteger;

/**
 * longの範囲で頭打ちにする四則演算。
 *
 * <p>このModはAE2のlong会計の上限に近い値を扱う。8Eのクラフトストレージや、4096×の加速カードなど、
 * 2^63級のクラフト回数) を日常的に扱うので、素の {@code +} / {@code *} はオーバーフローして負になる。
 * 負になった量はAE2側で「空」「0個」「壊れたCPU」として扱われ、原因が見えにくい。
 * 溢れたら{@link Long#MAX_VALUE}に張り付ける。負方向なら{@link Long#MIN_VALUE}に張り付ける。</p>
 *
 * <p>Mixinからも呼ぶので、Minecraft / AE2のクラスには依存させないこと。</p>
 */
public final class SaturatingMath {

    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private static final BigInteger LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE);

    private SaturatingMath() {
    }

    public static long add(long left, long right) {
        long result = left + right;
        // 符号が同じ2数を足して結果の符号だけ変わったら溢れている
        if (((left ^ result) & (right ^ result)) < 0) {
            return left < 0 ? Long.MIN_VALUE : Long.MAX_VALUE;
        }
        return result;
    }

    public static long multiply(long left, long right) {
        long high = Math.multiplyHigh(left, right);
        long low = left * right;
        // 128 bitの積の上位が下位の符号拡張になっていればlongに収まっている
        if (high == (low >> 63)) {
            return low;
        }
        return (left ^ right) < 0 ? Long.MIN_VALUE : Long.MAX_VALUE;
    }

    /** longに収まらない値は端に張り付けて返す。 */
    public static long toLong(BigInteger value) {
        if (value.compareTo(LONG_MAX) > 0) {
            return Long.MAX_VALUE;
        }
        if (value.compareTo(LONG_MIN) < 0) {
            return Long.MIN_VALUE;
        }
        return value.longValue();
    }
}
