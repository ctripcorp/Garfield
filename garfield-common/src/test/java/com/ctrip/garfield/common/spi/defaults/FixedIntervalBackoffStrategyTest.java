package com.ctrip.garfield.common.spi.defaults;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FixedIntervalBackoffStrategyTest {

    @Test
    void computeDelay_alwaysReturnsSameInterval() {
        FixedIntervalBackoffStrategy strategy = new FixedIntervalBackoffStrategy(1000, 3);
        assertEquals(1000, strategy.computeDelay(1));
        assertEquals(1000, strategy.computeDelay(2));
        assertEquals(1000, strategy.computeDelay(3));
    }

    @Test
    void maxRetries_returnsConfiguredValue() {
        FixedIntervalBackoffStrategy strategy = new FixedIntervalBackoffStrategy(1000, 5);
        assertEquals(5, strategy.maxRetries());
    }
}
