package jp.main.taikun.insaneae.integration.aco;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AcoBigIntegerLimitBridgeTest {
    @Test
    void formatsTheoreticalLimitWithoutAllocatingEveryDigit() {
        assertEquals(
                "10^16384 - 1 B",
                AcoBigIntegerLimitBridge.formatTheoreticalMaximum(16_384));
    }

    @Test
    void rejectsInvalidDigitLimit() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AcoBigIntegerLimitBridge.formatTheoreticalMaximum(0));
    }
}
