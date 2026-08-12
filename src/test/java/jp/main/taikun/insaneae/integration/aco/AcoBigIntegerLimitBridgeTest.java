package jp.main.taikun.insaneae.integration.aco;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class AcoBigIntegerLimitBridgeTest {
    @Test
    void formatsTheoreticalLimitWithoutAllocatingEveryDigit() {
        assertEquals(
                "10^16384 - 1 B",
                AcoBigIntegerLimitBridge.formatTheoreticalMaximum(
                        BigInteger.TEN.pow(16_384).subtract(BigInteger.ONE)));
    }

    @Test
    void formatsConfiguredBinaryLimitInScientificNotation() {
        assertEquals(
                "1.844 × 10^19 B",
                AcoBigIntegerLimitBridge.formatTheoreticalMaximum(
                        BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE)));
    }

    @Test
    void rejectsInvalidDigitLimit() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AcoBigIntegerLimitBridge.formatTheoreticalMaximum(BigInteger.ZERO));
    }
}
