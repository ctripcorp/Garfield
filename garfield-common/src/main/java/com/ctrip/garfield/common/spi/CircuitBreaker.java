package com.ctrip.garfield.common.spi;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * SPI for circuit breaker protection around storage engine operations.
 *
 * <p>Each {@code commandKey} identifies an independent circuit. When the circuit
 * opens (too many failures), calls are short-circuited to the {@code fallback}.
 *
 * @see com.ctrip.garfield.common.spi.defaults.Resilience4jCircuitBreaker
 * @author Trip.com Group
 */
public interface CircuitBreaker {
    <T> T execute(String commandKey, Supplier<T> action, Function<Throwable, T> fallback);
}
