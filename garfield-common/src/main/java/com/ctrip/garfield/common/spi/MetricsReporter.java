package com.ctrip.garfield.common.spi;

import com.ctrip.garfield.common.spi.observation.CompensationObservation;
import com.ctrip.garfield.common.spi.observation.ReadObservation;
import com.ctrip.garfield.common.spi.observation.WriteObservation;

/**
 * SPI for recording read, write, and compensation metrics.
 *
 * @author Trip.com Group
 */
public interface MetricsReporter {
    default void recordWrite(WriteObservation observation) {}
    default void recordRead(ReadObservation observation) {}
    default void recordCompensation(CompensationObservation observation) {}
}
