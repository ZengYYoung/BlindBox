package com.blindbox.resilience;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Per-instance circuit breaker. Only wrap Redis operations, never business validation. */
public final class RedisCircuitBreaker {
    private enum State { CLOSED, OPEN, HALF_OPEN }
    public static final class OpenException extends RuntimeException {
        public OpenException() { super("Redis circuit is open"); }
    }

    private final String name;
    private final MeterRegistry metrics;
    private final int threshold;
    private final long openNanos;
    private final LongSupplier clock;
    private State state = State.CLOSED;
    private long generation;
    private long openedAt;
    private int failures;
    private boolean probeInFlight;

    public RedisCircuitBreaker(String name, int threshold, Duration openDuration, MeterRegistry metrics) {
        this(name, threshold, openDuration, metrics, System::nanoTime);
    }
    public RedisCircuitBreaker(String name, int threshold, Duration openDuration, MeterRegistry metrics, LongSupplier clock) {
        if (threshold < 1 || openDuration.isZero() || openDuration.isNegative())
            throw new IllegalArgumentException("Invalid Redis circuit breaker settings");
        this.name = name; this.threshold = threshold; this.openNanos = openDuration.toNanos();
        this.metrics = metrics; this.clock = clock;
        metrics.gauge("blindbox.redis.circuit.state", java.util.List.of(io.micrometer.core.instrument.Tag.of("dependency", name)),
            this, circuit -> circuit.stateValue());
    }

    public <T> T execute(Supplier<T> operation) {
        final long ticket;
        final boolean probe;
        synchronized (this) {
            if (state == State.OPEN && clock.getAsLong() - openedAt >= openNanos) {
                state = State.HALF_OPEN; generation++; probeInFlight = false;
                transition("half_open");
            }
            if (state == State.OPEN || (state == State.HALF_OPEN && probeInFlight)) {
                metrics.counter("blindbox.redis.circuit.calls", "dependency", name, "outcome", "rejected").increment();
                throw new OpenException();
            }
            probe = state == State.HALF_OPEN;
            if (probe) probeInFlight = true;
            ticket = generation;
        }
        try {
            T value = operation.get();
            synchronized (this) {
                metrics.counter("blindbox.redis.circuit.calls", "dependency", name, "outcome", "success").increment();
                if (ticket == generation) {
                    if (probe && state == State.HALF_OPEN) {
                        state = State.CLOSED; generation++; probeInFlight = false; failures = 0;
                        transition("closed");
                    } else if (!probe && state == State.CLOSED) failures = 0;
                }
            }
            return value;
        } catch (RuntimeException | Error failure) {
            synchronized (this) {
                metrics.counter("blindbox.redis.circuit.calls", "dependency", name, "outcome", "failure").increment();
                if (ticket == generation && (probe || (state == State.CLOSED && ++failures >= threshold))) {
                    state = State.OPEN; generation++; openedAt = clock.getAsLong(); probeInFlight = false;
                    transition("open");
                }
            }
            throw failure;
        }
    }
    private void transition(String target) {
        metrics.counter("blindbox.redis.circuit.transitions", "dependency", name, "state", target).increment();
    }
    private synchronized int stateValue() { return switch (state) { case CLOSED -> 0; case OPEN -> 1; case HALF_OPEN -> 2; }; }
}
