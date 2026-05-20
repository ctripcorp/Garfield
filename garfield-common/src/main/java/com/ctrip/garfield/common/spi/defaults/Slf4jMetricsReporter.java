package com.ctrip.garfield.common.spi.defaults;

import com.ctrip.garfield.common.spi.MetricsReporter;
import com.ctrip.garfield.common.spi.observation.CompensationObservation;
import com.ctrip.garfield.common.spi.observation.ReadObservation;
import com.ctrip.garfield.common.spi.observation.WriteObservation;
import lombok.extern.slf4j.Slf4j;

/**
 * Default metrics reporter that logs observations via SLF4J.
 *
 * @author Trip.com Group
 */
@Slf4j
public class Slf4jMetricsReporter implements MetricsReporter {

    @Override
    public void recordWrite(WriteObservation observation) {
        log.info("WRITE {}", observation);
    }

    @Override
    public void recordRead(ReadObservation observation) {
        log.info("READ {}", observation);
    }

    @Override
    public void recordCompensation(CompensationObservation observation) {
        log.info("COMPENSATION {}", observation);
    }
}
