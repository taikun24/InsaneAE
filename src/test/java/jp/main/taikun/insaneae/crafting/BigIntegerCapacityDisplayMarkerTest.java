package jp.main.taikun.insaneae.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;
//? if <1.21 {
/*import static org.junit.jupiter.api.Assertions.assertTrue;
import net.minecraft.network.chat.Component;
*///?}

class BigIntegerCapacityDisplayMarkerTest {

    @Test
    void formatsTwoEightEStorageBlocksAsSixteenE() {
        BigInteger sixteenEib = BigInteger.ONE.shiftLeft(64);
        //? if >=1.21 {
        var display = BigIntegerCapacityDisplayValue.capture(sixteenEib);
        assertEquals("16E", display.format());
        //?} else {
        /*var display = BigIntegerCapacityDisplayMarker.DisplayValue.capture(sixteenEib);
        assertEquals("16E", BigIntegerCapacityDisplayMarker.format(display));
        *///?}
    }

    @Test
    void keepsScientificNotationForVeryLargeValues() {
        BigInteger huge = BigInteger.TEN.pow(100);
        //? if >=1.21 {
        var display = BigIntegerCapacityDisplayValue.capture(huge);
        assertEquals("1 × 10^100 B", display.format());
        //?} else {
        /*var display = BigIntegerCapacityDisplayMarker.DisplayValue.capture(huge);
        assertEquals("1 × 10^100 B", BigIntegerCapacityDisplayMarker.format(display));
        *///?}
    }

    @Test
    //? if >=1.21 {
    void markerPayloadRoundTripsExactCapacity() {
    //?} else {
    /*void carriesExactCapacityWithoutGivingUnnamedCpuAVisibleName() {
    *///?}
        BigInteger sixteenEib = BigInteger.ONE.shiftLeft(64);
        //? if >=1.21 {
        var display = BigIntegerCapacityDisplayValue.capture(sixteenEib);

        assertEquals(display,
                BigIntegerCapacityDisplayValue.decode(display.encode()).orElseThrow());
        //?} else {
        /*Component marked = BigIntegerCapacityDisplayMarker.mark(Component.empty(), sixteenEib);

        assertEquals("", marked.getString());
        assertTrue(BigIntegerCapacityDisplayMarker.read(marked).isPresent());
        *///?}
    }
}
