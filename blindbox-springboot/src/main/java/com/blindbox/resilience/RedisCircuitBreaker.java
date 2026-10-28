package com.blindbox.resilience;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.degrade.circuitbreaker.EventObserverRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** Sentinel protects Redis operations only; callers decide their read/write fallback. */
public final class RedisCircuitBreaker implements AutoCloseable {
    public static final class OpenException extends RuntimeException {
        public OpenException(BlockException cause) { super("Sentinel blocked Redis resource", cause); }
    }
    private final String resource;
    private final String name;
    private final MeterRegistry metrics;
    private final String observerId = "blindbox-" + UUID.randomUUID();
    private final AtomicInteger state = new AtomicInteger();
    private int inFlight;

    public RedisCircuitBreaker(String name, int threshold, Duration openDuration, MeterRegistry metrics) {
        if (threshold < 1 || openDuration.isZero() || openDuration.isNegative())
            throw new IllegalArgumentException("Invalid Sentinel circuit settings");
        long millis = openDuration.toMillis();
        if (millis < 1 || millis > 2_147_483_000L)
            throw new IllegalArgumentException("Sentinel open duration must be 1..2147483000 ms");
        int seconds = (int) ((millis + 999) / 1000);
        this.name = name; this.resource = "blindbox.redis." + name; this.metrics = metrics;
        DegradeRule rule = new DegradeRule(resource)
            .setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_COUNT)
            .setCount(threshold - 1).setMinRequestAmount(1).setStatIntervalMs(1000).setTimeWindow(seconds);
        if (!DegradeRuleManager.isValidRule(rule))
            throw new IllegalArgumentException("Invalid Sentinel rule for " + resource);
        boolean updated = DegradeRuleManager.setRulesForResource(resource, Set.of(rule));
        // Sentinel returns false when an identical rule is already installed.
        if (!updated && DegradeRuleManager.getRulesOfResource(resource).stream().noneMatch(existing ->
            existing.getGrade() == rule.getGrade() && existing.getCount() == rule.getCount()
                && existing.getTimeWindow() == rule.getTimeWindow()
                && existing.getMinRequestAmount() == rule.getMinRequestAmount()
                && existing.getStatIntervalMs() == rule.getStatIntervalMs()))
            throw new IllegalStateException("Unable to register Sentinel rule for " + resource);
        metrics.gauge("blindbox.redis.circuit.state", List.of(Tag.of("dependency", name)), state);
        EventObserverRegistry.getInstance().addStateChangeObserver(observerId, (previous, next, changed, value) -> {
            if (!resource.equals(changed.getResource())) return;
            state.set(switch (next) { case CLOSED -> 0; case OPEN -> 1; case HALF_OPEN -> 2; });
            metrics.counter("blindbox.redis.circuit.transitions", "dependency", name,
                "state", next.name().toLowerCase(Locale.ROOT)).increment();
        });
    }

    public <T> T execute(Supplier<T> operation) {
        synchronized (this) {
            // Do not let a completion from before OPEN end a newer HALF_OPEN probe.
            if (state.get() == 1 && inFlight > 0) {
                metrics.counter("blindbox.redis.circuit.calls", "dependency", name, "outcome", "rejected").increment();
                throw new OpenException(null);
            }
            inFlight++;
        }
        try {
            final Entry entry;
            try { entry = SphU.entry(resource); }
            catch (BlockException blocked) {
                metrics.counter("blindbox.redis.circuit.calls", "dependency", name, "outcome", "rejected").increment();
                throw new OpenException(blocked);
            }
            try (entry) {
                try {
                    T value = operation.get();
                    metrics.counter("blindbox.redis.circuit.calls", "dependency", name, "outcome", "success").increment();
                    return value;
                } catch (RuntimeException | Error failure) {
                    Tracer.traceEntry(failure, entry);
                    metrics.counter("blindbox.redis.circuit.calls", "dependency", name, "outcome", "failure").increment();
                    throw failure;
                }
            }
        } finally {
            synchronized (this) { inFlight--; }
        }
    }
    @Override public void close() {
        EventObserverRegistry.getInstance().removeStateChangeObserver(observerId);
    }
}
