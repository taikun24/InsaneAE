package jp.main.taikun.insaneae.quantum;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

/** Exact taskがAE2の通常maxPatterns=1へ誤って縮退しないことを検証する。 */
class BulkExecutionWindowTest {
    @Test
    void exactWindowUsesQuantumProviderCapacity() {
        BigInteger oneExa = BigInteger.TEN.pow(18);

        // 1E全量を走査せず、Quantum CPUの一tick容量だけを窓へ切り出す。
        assertEquals(65_536L, BulkExecutionWindow.forExactTask(oneExa, 65_536L));
    }

    @Test
    void exactWindowNeverExceedsLongOrRemaining() {
        // signed long境界を越える残量でも、AE2へ渡す窓だけは正確なlongに収める。
        assertEquals(
                Long.MAX_VALUE,
                BulkExecutionWindow.forExactTask(
                        BigInteger.ONE.shiftLeft(128), Long.MAX_VALUE));
        assertEquals(
                7L,
                BulkExecutionWindow.forExactTask(BigInteger.valueOf(7L), 65_536L));
    }

    @Test
    void exactBulkCallConsumesOneCpuOperation() {
        assertEquals(1, BulkExecutionWindow.consumedExactOperation(65_536L));
        assertEquals(0, BulkExecutionWindow.consumedExactOperation(0L));
    }
}
