package jp.main.taikun.insaneae.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LogOnceTest {

    @BeforeEach
    void reset() {
        LogOnce.resetForTests();
    }

    @Test
    void onlyTheFirstCallPerKeyPasses() {
        assertTrue(LogOnce.first("a"));
        assertFalse(LogOnce.first("a"));
        assertFalse(LogOnce.first("a"));
        assertTrue(LogOnce.first("b"));
    }

    @Test
    void stopsRememberingAfterTheCap() {
        for (int i = 0; i < LogOnce.MAX_KEYS; i++) {
            assertTrue(LogOnce.first("key-" + i));
        }
        // 上限を超えた新しい鍵は出さない側に倒す (ログが増え続けないこと)。
        assertFalse(LogOnce.first("one-more"));
        assertFalse(LogOnce.first("key-0"));
    }
}
