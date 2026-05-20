package com.ctrip.garfield.common.spi.defaults;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExponentialBackoffStrategyTest {

    @Test
    void computeDelay_firstAttempt_returnsApproximatelyInitialInterval() {
        ExponentialBackoffStrategy strategy = new ExponentialBackoffStrategy(
                2000, 2.0, 163840000, 0.0, 16);
        assertEquals(2000, strategy.computeDelay(1));
    }

    @Test
    void computeDelay_secondAttempt_doublesInterval() {
        ExponentialBackoffStrategy strategy = new ExponentialBackoffStrategy(
                2000, 2.0, 163840000, 0.0, 16);
        assertEquals(4000, strategy.computeDelay(2));
    }

    @Test
    void computeDelay_respectsMaxInterval() {
        ExponentialBackoffStrategy strategy = new ExponentialBackoffStrategy(
                2000, 2.0, 10000, 0.0, 16);
        long delay = strategy.computeDelay(10);
        assertTrue(delay <= 10000, "delay should not exceed maxIntervalMs");
    }

    @Test
    void computeDelay_withJitter_variesResult() {
        ExponentialBackoffStrategy strategy = new ExponentialBackoffStrategy(
                2000, 2.0, 163840000, 0.1, 16);
        long d1 = strategy.computeDelay(5);
        long baseDelay = (long) (2000 * Math.pow(2.0, 4));
        long minExpected = (long) (baseDelay * 0.9);
        long maxExpected = (long) (baseDelay * 1.1);
        assertTrue(d1 >= minExpected && d1 <= maxExpected);
    }

    @Test
    void computeDelay_neverReturnsNegative() {
        ExponentialBackoffStrategy strategy = new ExponentialBackoffStrategy(
                1, 2.0, 100, 0.5, 16);
        for (int i = 1; i <= 20; i++) {
            assertTrue(strategy.computeDelay(i) >= 0);
        }
    }

    @Test
    void maxRetries_returnsConfiguredValue() {
        ExponentialBackoffStrategy strategy = new ExponentialBackoffStrategy(
                2000, 2.0, 163840000, 0.1, 16);
        assertEquals(16, strategy.maxRetries());
    }
}
