package jp.main.taikun.insaneae.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class InsaneCraftingUnitTypeTest {

    @Test
    void eightEUsesLongFacadeAndExactBigIntegerCapacity() {
        assertEquals(Long.MAX_VALUE, InsaneCraftingUnitType.STORAGE_8E.getStorageBytes());
        assertEquals(BigInteger.ONE.shiftLeft(63),
                InsaneCraftingUnitType.STORAGE_8E.exactStorageBytes());
    }

    @Test
    void twoEightEUnitsAddUpToSixteenE() {
        BigInteger combined = InsaneCraftingUnitType.STORAGE_8E.exactStorageBytes()
                .multiply(BigInteger.TWO);
        assertEquals(BigInteger.ONE.shiftLeft(64), combined);
    }
}
