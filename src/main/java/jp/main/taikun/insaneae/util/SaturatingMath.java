package jp.main.taikun.insaneae.util;

import java.math.BigInteger;

/**
 * long の範囲で頭打ちにする四則演算。
 *
 * <p>この Mod は AE2 の long 会計の上限際 (8E のクラフトストレージ、4096× の加速カード、
 * 2^63 級のクラフト回数) を日常的に踏むので、素の {@code +} / {@code *} は黙って負に回る。
 * 負になった量は AE2 側で「空」「0 個」「壊れた CPU」として扱われ、原因が見えにくい。
 * 溢れたら {@link Long#MAX_VALUE} (負方向なら {@link Long#MIN_VALUE}) に張り付ける。</p>
 *
 * <p>Mixin からも呼ぶので、Minecraft / AE2 のクラスには依存させないこと。</p>
 */
public final class SaturatingMath {

    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private static final BigInteger LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE);

    private SaturatingMath() {
    }

    public static long add(long left, long right) {
        long result = left + right;
        // 符号が同じ 2 数を足して結果の符号だけ変わったら溢れている。
        if (((left ^ result) & (right ^ result)) < 0) {
            return left < 0 ? Long.MIN_VALUE : Long.MAX_VALUE;
        }
        return result;
    }

    public static long multiply(long left, long right) {
        long high = Math.multiplyHigh(left, right);
        long low = left * right;
        // 128 bit の積の上位が下位の符号拡張になっていれば long に収まっている。
        if (high == (low >> 63)) {
            return low;
        }
        return (left ^ right) < 0 ? Long.MIN_VALUE : Long.MAX_VALUE;
    }

    /** long に収まらない値は端に張り付けて返す。 */
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
