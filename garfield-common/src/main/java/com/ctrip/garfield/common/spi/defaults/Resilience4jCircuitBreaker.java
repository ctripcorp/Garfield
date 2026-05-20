package com.ctrip.garfield.common.spi.defaults;

import com.ctrip.garfield.common.spi.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Resilience4j-based circuit breaker implementation.
 *
 * @author Trip.com Group
 */
public class Resilience4jCircuitBreaker implements CircuitBreaker {

    private final CircuitBreakerRegistry registry;

    public Resilience4jCircuitBreaker() {
        this.registry = CircuitBreakerRegistry.ofDefaults();
    }

    public Resilience4jCircuitBreaker(CircuitBreakerRegistry registry) {
        this.registry = registry;
    }

    @Override
    public <T> T execute(String commandKey, Supplier<T> action, Function<Throwable, T> fallback) {
        var cb = registry.circuitBreaker(commandKey);
        try {
            return cb.executeSupplier(action);
        } catch (Exception e) {
            return fallback.apply(e);
        }
    }
}
