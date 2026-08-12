package jp.main.taikun.insaneae.crafting;

import java.math.BigInteger;
import java.util.Optional;

/** MinecraftのComponent型に依存せず、BigInteger容量の同期値と表示を扱う。 */
public record BigIntegerCapacityDisplayValue(int decimalDigits, String leadingDigits) {
    /** 16Eなど通常のクラスタ容量を正確な二進単位へ戻せるよう保持する最大桁数。 */
    private static final int LEADING_DIGITS = 64;
    /** 科学表記へ使う有効数字の数。 */
    private static final int SCIENTIFIC_SIGNIFICANT_DIGITS = 4;
    /** 1 EiBを表すバイト数。 */
    private static final BigInteger EIB_BYTES = BigInteger.ONE.shiftLeft(60);
    /** UI幅を守りつつ整数E表記を使える最大桁数。 */
    private static final int MAX_EIB_INTEGER_DIGITS = 6;
    private static final String SEPARATOR = ",";

    public BigIntegerCapacityDisplayValue {
        // 桁数と先頭桁の形式を検証し、偽造した表示データを受け付けない。
        if (decimalDigits < 1 || leadingDigits == null
                || leadingDigits.isEmpty()
                || leadingDigits.length() > LEADING_DIGITS
                || !leadingDigits.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("invalid BigInteger capacity display value");
        }
        // 0以外の値を先頭0で表すと指数がずれるため拒否する。
        if (decimalDigits > 1 && leadingDigits.charAt(0) == '0') {
            throw new IllegalArgumentException("BigInteger capacity display has a leading zero");
        }
    }

    public static BigIntegerCapacityDisplayValue capture(BigInteger capacity) {
        String decimal = capacity.toString();
        int leadingLength = Math.min(LEADING_DIGITS, decimal.length());
        return new BigIntegerCapacityDisplayValue(
                decimal.length(), decimal.substring(0, leadingLength));
    }

    String encode() {
        return decimalDigits + SEPARATOR + leadingDigits;
    }

    static Optional<BigIntegerCapacityDisplayValue> decode(String encoded) {
        // 区切りが一つだけの正規形以外は曖昧に解釈しない。
        if (encoded == null) {
            return Optional.empty();
        }
        int separator = encoded.indexOf(SEPARATOR);
        if (separator <= 0 || separator != encoded.lastIndexOf(SEPARATOR)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BigIntegerCapacityDisplayValue(
                    Integer.parseInt(encoded.substring(0, separator)),
                    encoded.substring(separator + 1)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    /** CPU一覧・ツールチップへ表示する短い容量文字列を返す。 */
    public String format() {
        String exactEib = formatExactEib();
        // 正確なEiB整数へ戻せる容量は、16Eのような短いカタログ表記を優先する。
        if (exactEib != null) {
            return exactEib;
        }

        int significant = Math.min(SCIENTIFIC_SIGNIFICANT_DIGITS, leadingDigits.length());
        String fraction = leadingDigits.substring(1, significant);
        // 仮数末尾の0を除いて、表示幅を増やさない。
        while (fraction.endsWith("0")) {
            fraction = fraction.substring(0, fraction.length() - 1);
        }
        String mantissa = fraction.isEmpty()
                ? leadingDigits.substring(0, 1)
                : leadingDigits.substring(0, 1) + "." + fraction;
        return mantissa + " × 10^" + (decimalDigits - 1) + " B";
    }

    /** 全桁を同期できた値がEiBの整数倍なら、短いE表記へ変換する。 */
    private String formatExactEib() {
        // 先頭桁しか持たない巨大値を、正確な容量だと推測しない。
        if (leadingDigits.length() != decimalDigits) {
            return null;
        }
        BigInteger exact = new BigInteger(leadingDigits);
        BigInteger[] quotientAndRemainder = exact.divideAndRemainder(EIB_BYTES);
        // EiBの整数倍でなければ、科学表記へ戻す。
        if (quotientAndRemainder[1].signum() != 0) {
            return null;
        }
        String eib = quotientAndRemainder[0].toString();
        // 長い整数をそのままGUIへ出さず、幅を超える場合は科学表記へ戻す。
        if (eib.length() > MAX_EIB_INTEGER_DIGITS) {
            return null;
        }
        return eib + "E";
    }
}
