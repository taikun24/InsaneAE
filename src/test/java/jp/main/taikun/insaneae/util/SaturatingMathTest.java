package jp.main.taikun.insaneae.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class SaturatingMathTest {

    @Test
    void addStaysExactInsideLongRange() {
        assertEquals(5L, SaturatingMath.add(2L, 3L));
        assertEquals(-1L, SaturatingMath.add(Long.MAX_VALUE, Long.MIN_VALUE));
        assertEquals(Long.MAX_VALUE, SaturatingMath.add(Long.MAX_VALUE - 1, 1L));
    }

    @Test
    void addSticksToTheEdgeOnOverflow() {
        assertEquals(Long.MAX_VALUE, SaturatingMath.add(Long.MAX_VALUE, 1L));
        assertEquals(Long.MAX_VALUE, SaturatingMath.add(Long.MAX_VALUE, Long.MAX_VALUE));
        assertEquals(Long.MIN_VALUE, SaturatingMath.add(Long.MIN_VALUE, -1L));
    }

    @Test
    void multiplyStaysExactInsideLongRange() {
        assertEquals(4096L * 4096L, SaturatingMath.multiply(4096L, 4096L));
        assertEquals(-6L, SaturatingMath.multiply(-2L, 3L));
        assertEquals(Long.MIN_VALUE, SaturatingMath.multiply(Long.MIN_VALUE, 1L));
        assertEquals(0L, SaturatingMath.multiply(Long.MAX_VALUE, 0L));
        // 8^21 = 2^63 の一歩手前はちょうど収まる。
        assertEquals(1L << 62, SaturatingMath.multiply(1L << 31, 1L << 31));
    }

    @Test
    void multiplySticksToTheEdgeOnOverflow() {
        assertEquals(Long.MAX_VALUE, SaturatingMath.multiply(1L << 32, 1L << 31));
        assertEquals(Long.MAX_VALUE, SaturatingMath.multiply(-(1L << 32), -(1L << 31)));
        assertEquals(Long.MIN_VALUE, SaturatingMath.multiply(1L << 32, -(1L << 32)));
        assertEquals(Long.MAX_VALUE, SaturatingMath.multiply(Long.MIN_VALUE, -1L));
    }

    @Test
    void toLongClampsBothEnds() {
        assertEquals(42L, SaturatingMath.toLong(BigInteger.valueOf(42L)));
        assertEquals(Long.MAX_VALUE, SaturatingMath.toLong(BigInteger.valueOf(Long.MAX_VALUE)));
        assertEquals(Long.MAX_VALUE, SaturatingMath.toLong(BigInteger.ONE.shiftLeft(64)));
        assertEquals(Long.MIN_VALUE, SaturatingMath.toLong(BigInteger.ONE.shiftLeft(64).negate()));
    }
}
