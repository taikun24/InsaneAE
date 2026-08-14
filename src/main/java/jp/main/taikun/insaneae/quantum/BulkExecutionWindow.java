package jp.main.taikun.insaneae.quantum;

import java.math.BigInteger;

/**
 * BigIntegerの残量を、Quantum CPUが一回の呼び出しで安全に受理できるlong窓へ変換する純粋関数。
 *
 * <p>注文全体をlongへ変換するクラスではない。longへ落とすのは、実際に一度の
 * {@code pushPatternBulk}へ渡す窓だけであり、呼び出し後の残量はBigIntegerのまま保持する。</p>
 */
final class BulkExecutionWindow {
    /** AE2の実行引数へ渡せる符号付きlongの最大値。正本の上限ではない。 */
    private static final BigInteger LONG_MAXIMUM = BigInteger.valueOf(Long.MAX_VALUE);

    private BulkExecutionWindow() {
    }

    static long forExactTask(
            BigInteger remaining,
            int remainingOperations,
            long providerCapacity,
            boolean fusedOperation) {
        // 無効な残量・CPU予算・Provider容量は、入力を所有せず待機させる。
        if (remaining == null || remaining.signum() <= 0
                || remainingOperations <= 0 || providerCapacity <= 0) {
            return 0L;
        }

        // 正本全体ではなく、この一回の窓だけをlongへ正確に変換する。
        long boundedRemaining = remaining.min(LONG_MAXIMUM).longValueExact();
        long window = Math.min(boundedRemaining, providerCapacity);
        // Task FusionなしではAE2の今回の物理操作予算も守る。
        if (!fusedOperation) {
            window = Math.min(window, remainingOperations);
        }
        return window;
    }

    /**
     * ACO Exact task専用の窓。AE2の通常long操作数ではなく、Quantum CPUのBulk容量を上限にする。
     * Exact taskはInsaneAEが一回のBulk投入を一操作として所有するため、Task Fusionカードへ依存しない。
     */
    static long forExactTask(BigInteger remaining, long providerCapacity) {
        // 正本全体をlongへ変換せず、この一回のProvider容量だけを窓として切り出す。
        if (remaining == null || remaining.signum() <= 0 || providerCapacity <= 0L) {
            return 0L;
        }
        return remaining.min(LONG_MAXIMUM)
                .min(BigInteger.valueOf(providerCapacity))
                .longValueExact();
    }

    static int consumedOperations(long accepted, boolean fusedOperation) {
        // Providerが何も受理しなければ、この呼び出しはCPU操作を消費していない。
        if (accepted <= 0L) {
            return 0;
        }
        // FusionありはBulk一回を一操作、なしは実際のクラフト回数を操作数とする。
        return fusedOperation ? 1 : Math.toIntExact(accepted);
    }

    /** Exact taskではBulk呼び出し一回をAE2の一操作として扱う。 */
    static int consumedExactOperation(long accepted) {
        // Providerが何も受理しなければ、CPU操作も進捗も消費しない。
        return accepted > 0L ? 1 : 0;
    }
}
