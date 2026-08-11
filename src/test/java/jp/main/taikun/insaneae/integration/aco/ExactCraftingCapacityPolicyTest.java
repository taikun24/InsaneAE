package jp.main.taikun.insaneae.integration.aco;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class ExactCraftingCapacityPolicyTest {
    /** 4 EiBを2台つないだ正確な合計。longの正数上限より1大きい。 */
    private static final BigInteger TWO_TIMES_FOUR_EIB = BigInteger.ONE.shiftLeft(63);
    /** 8 EiBを2台つないだ正確な16 EiB容量。 */
    private static final BigInteger TWO_TIMES_EIGHT_EIB = BigInteger.ONE.shiftLeft(64);

    @Test
    void acceptsOrderEqualToTwoFourEStorageBlocks() {
        assertTrue(ExactCraftingCapacityPolicy.fits(
                TWO_TIMES_FOUR_EIB,
                TWO_TIMES_FOUR_EIB));
    }

    @Test
    void rejectsOrderOneByteOverTwoFourEStorageBlocks() {
        assertFalse(ExactCraftingCapacityPolicy.fits(
                TWO_TIMES_FOUR_EIB.add(BigInteger.ONE),
                TWO_TIMES_FOUR_EIB));
    }

    @Test
    void acceptsSixteenEibOrderWithTwoEightEStorageBlocks() {
        assertTrue(ExactCraftingCapacityPolicy.fits(
                TWO_TIMES_EIGHT_EIB,
                TWO_TIMES_EIGHT_EIB));
    }

    @Test
    void rejectsOnlyAfterExceedingTheFullSixteenEibCapacity() {
        assertFalse(ExactCraftingCapacityPolicy.fits(
                TWO_TIMES_EIGHT_EIB.add(BigInteger.ONE),
                TWO_TIMES_EIGHT_EIB));
    }

    @Test
    void keepsOrdinaryLongSizedOrdersOnTheSameComparisonRule() {
        BigInteger ordinaryOrder = BigInteger.valueOf(Long.MAX_VALUE);
        assertTrue(ExactCraftingCapacityPolicy.fits(ordinaryOrder, ordinaryOrder));
    }
}
