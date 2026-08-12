package jp.main.taikun.insaneae.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class BigIntegerCapacityDisplayMarkerTest {

    @Test
    void formatsTwoEightEStorageBlocksAsSixteenE() {
        BigInteger sixteenEib = BigInteger.ONE.shiftLeft(64);
        var display = BigIntegerCapacityDisplayValue.capture(sixteenEib);
        assertEquals("16E", display.format());
    }

    @Test
    void keepsScientificNotationForVeryLargeValues() {
        BigInteger huge = BigInteger.TEN.pow(100);
        var display = BigIntegerCapacityDisplayValue.capture(huge);
        assertEquals("1 × 10^100 B", display.format());
    }

    @Test
    void markerPayloadRoundTripsExactCapacity() {
        BigInteger sixteenEib = BigInteger.ONE.shiftLeft(64);
        var display = BigIntegerCapacityDisplayValue.capture(sixteenEib);

        assertEquals(display,
                BigIntegerCapacityDisplayValue.decode(display.encode()).orElseThrow());
    }
}
