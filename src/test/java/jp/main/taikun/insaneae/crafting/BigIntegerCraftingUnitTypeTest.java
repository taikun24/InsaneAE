package jp.main.taikun.insaneae.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BigIntegerCraftingUnitTypeTest {

    @Test
    void usesLongMaximumOnlyAsAe2CompatibilityFacade() {
        BigInteger exact = BigInteger.TEN.pow(64).subtract(BigInteger.ONE);
        var type = new BigIntegerCraftingUnitType(() -> Optional.of(exact));

        assertEquals(Long.MAX_VALUE, type.getStorageBytes());
        assertEquals(exact, type.exactStorageBytes());
        assertEquals(0, type.getAcceleratorThreads());
    }

    @Test
    void fallsBackToLongMaximumWithoutAcoCapacityApi() {
        var type = new BigIntegerCraftingUnitType(Optional::<BigInteger>empty);

        assertEquals(BigInteger.valueOf(Long.MAX_VALUE), type.exactStorageBytes());
    }
}
