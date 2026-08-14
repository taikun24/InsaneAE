package jp.main.taikun.insaneae.quantum;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class BulkExecutionWindowTest {
    @Test
    void keepsTheExactRemainderAndUsesProviderCapacityAsTheWindow() {
        BigInteger requested = BigInteger.TEN.pow(64);
        long window = BulkExecutionWindow.forExactTask(requested, 65_536L);
        BigInteger remainder = requested.subtract(BigInteger.valueOf(window));

        assertEquals(65_536L, window);
        assertEquals(requested, remainder.add(BigInteger.valueOf(window)));
    }

    @Test
    void clampsOnlyThePhysicalWindowToLongMaximum() {
        BigInteger requested = BigInteger.ONE.shiftLeft(128);

        assertEquals(Long.MAX_VALUE, BulkExecutionWindow.forExactTask(requested, Long.MAX_VALUE));
        assertEquals(1, BulkExecutionWindow.consumedExactOperation(Long.MAX_VALUE));
    }

    @Test
    void rejectsInvalidWindowsWithoutChangingTheBigIntegerSource() {
        BigInteger requested = BigInteger.valueOf(1234L);

        assertEquals(0L, BulkExecutionWindow.forExactTask(requested, 0L));
        assertEquals(0L, BulkExecutionWindow.forExactTask(BigInteger.ZERO, 10L));
    }
}
