package jp.main.taikun.insaneae.quantum;

import java.math.BigInteger;

/**
 * BigIntegerの残量を、AE2/機械側へ渡せる一回のlong窓へ変換する純粋関数。
 * 実行回数の正本はBigIntegerのまま保持し、ここでだけ物理窓へ落とす。
 */
public final class BulkExecutionWindow {
    private static final BigInteger LONG_MAXIMUM = BigInteger.valueOf(Long.MAX_VALUE);

    private BulkExecutionWindow() {
    }

    /** 旧通常Batch向けの互換窓。物理操作数とプロバイダ容量の両方を尊重する。 */
    public static long forExactTask(
            BigInteger remaining,
            int remainingOperations,
            long providerCapacity,
            boolean fusedOperation) {
        if (remaining == null || remaining.signum() <= 0 || remainingOperations <= 0
                || providerCapacity <= 0) {
            return 0L;
        }
        long boundedRemaining = remaining.min(LONG_MAXIMUM).longValueExact();
        if (fusedOperation) {
            return Math.min(boundedRemaining, providerCapacity);
        }
        return Math.min(
                Math.min(boundedRemaining, providerCapacity),
                remainingOperations);
    }

    /** Quantum CPU向けの窓。AE2が渡すmaxPatterns=1ではBulk量を制限しない。 */
    public static long forExactTask(BigInteger remaining, long providerCapacity) {
        if (remaining == null || remaining.signum() <= 0 || providerCapacity <= 0) {
            return 0L;
        }
        // BigInteger正本から、プロバイダが一窓で安全に受理できるlongだけを切り出す。
        return Math.min(remaining.min(LONG_MAXIMUM).longValueExact(), providerCapacity);
    }

    /** 通常Batchで物理的に消費した操作数をintへ戻す。 */
    public static int consumedOperations(long accepted, boolean fusedOperation) {
        if (accepted <= 0L) {
            return 0;
        }
        return fusedOperation ? 1 : Math.toIntExact(accepted);
    }

    /** Quantum CPUの一回のBulk受理をAE2の一操作として扱う。 */
    public static int consumedExactOperation(long accepted) {
        return accepted > 0L ? 1 : 0;
    }
}
