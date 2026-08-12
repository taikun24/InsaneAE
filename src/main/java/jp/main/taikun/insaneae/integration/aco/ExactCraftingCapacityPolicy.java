package jp.main.taikun.insaneae.integration.aco;

import java.math.BigInteger;
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
}
