package jp.main.taikun.insaneae.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class BigIntegerCapacityDisplayMarkerTest {

    @Test
    void formatsTwoEightEStorageBlocksAsSixteenE() {
        BigInteger sixteenEib = BigInteger.ONE.shiftLeft(64);
        var display = BigIntegerCapacityDisplayMarker.DisplayValue.capture(sixteenEib);
        assertEquals("16E", BigIntegerCapacityDisplayMarker.format(display));
    }

    @Test
    void keepsScientificNotationForVeryLargeValues() {
        BigInteger huge = BigInteger.TEN.pow(100);
        var display = BigIntegerCapacityDisplayMarker.DisplayValue.capture(huge);
        assertEquals("1 × 10^100 B", BigIntegerCapacityDisplayMarker.format(display));
    }
}
