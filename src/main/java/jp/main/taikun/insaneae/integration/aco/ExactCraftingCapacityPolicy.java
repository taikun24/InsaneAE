package jp.main.taikun.insaneae.integration.aco;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.Objects;

/** ACOの正確な必要容量と、InsaneAE CPUの正確な容量を比較する純粋な判定。 */
public final class ExactCraftingCapacityPolicy {

    private ExactCraftingCapacityPolicy() {
    }

    /** 必要容量がCPU容量以下の場合だけ受理できる。 */
    public static boolean fits(BigInteger required, BigInteger capacity) {
        Objects.requireNonNull(required, "required");
        Objects.requireNonNull(capacity, "capacity");
        // 負値は破損した外部入力なので、容量比較へ流さず明示的に拒否する。
        if (required.signum() < 0 || capacity.signum() < 0) {
            return false;
        }
        return required.compareTo(capacity) <= 0;
    }

    /**
     * ログ用の短い表記。正確な値は{@code 10進16,384桁}まで伸びうるので展開しない。
     *
     * <p>拒否理由を読むのに必要なのは「どれだけ足りないか」の桁感だけなので、
     * long内はそのまま、それ以上は有効数字4桁の科学表記へ落とす。</p>
     */
    public static String describe(BigInteger amount) {
        Objects.requireNonNull(amount, "amount");
        // long内の値は丸めず読めるので、そのまま出す。
        if (amount.abs().bitLength() < Long.SIZE) {
            return amount.toString();
        }
        return new BigDecimal(amount).round(new MathContext(4)).toEngineeringString();
    }
}
