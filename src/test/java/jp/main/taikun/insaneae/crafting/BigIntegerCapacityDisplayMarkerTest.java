package jp.main.taikun.insaneae.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import net.minecraft.network.chat.Component;
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

    @Test
    void carriesExactCapacityWithoutGivingUnnamedCpuAVisibleName() {
        BigInteger sixteenEib = BigInteger.ONE.shiftLeft(64);
        Component marked = BigIntegerCapacityDisplayMarker.mark(Component.empty(), sixteenEib);

        assertEquals("", marked.getString());
        assertTrue(BigIntegerCapacityDisplayMarker.read(marked).isPresent());
    }
}
