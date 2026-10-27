package com.blindbox.core;

import com.blindbox.common.BusinessException;
import com.blindbox.resilience.RedisCircuitBreaker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Component
public class CatalogCache {
    private static final Logger log = LoggerFactory.getLogger(CatalogCache.class);
    private static final String GENERATION = "blindbox:catalog:generation";
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final Executor invalidations;
    private final MeterRegistry metrics;
    private final ReentrantLock[] loads = new ReentrantLock[64];
    private final AtomicBoolean scheduled = new AtomicBoolean();
    private final AtomicBoolean dirty = new AtomicBoolean();
    private final RedisCircuitBreaker circuit;

    public CatalogCache(StringRedisTemplate redis, ObjectMapper json,
                        @Qualifier("catalogInvalidationExecutor") Executor invalidations, MeterRegistry metrics,
                        @Qualifier("redisCacheCircuit") RedisCircuitBreaker circuit) {
        this.redis = redis; this.json = json; this.invalidations = invalidations; this.metrics = metrics; this.circuit = circuit;
        for (int i = 0; i < loads.length; i++) loads[i] = new ReentrantLock();
    }

    private String key(String suffix) {
        try {
            return circuit.execute(() -> {
                String generation = redis.opsForValue().get(GENERATION);
                if (generation == null) {
                    redis.opsForValue().setIfAbsent(GENERATION, Long.toString(System.currentTimeMillis()));
                    generation = redis.opsForValue().get(GENERATION);
                }
                return generation == null ? null : "blindbox:catalog:metadata:v2:" + generation + ":" + suffix;
            });
        } catch (Exception e) { cacheFailure(e); return null; }
    }
    private Map<String, Object> get(String key) {
        if (key == null) return null;
        try {
            String value = circuit.execute(() -> redis.opsForValue().get(key));
            return value == null ? null : json.readValue(value, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) { cacheFailure(e); return null; }
    }
    private void put(String key, Map<String, Object> value) {
        if (key == null) return;
        try {
            String encoded = json.writeValueAsString(value);
            circuit.execute(() -> { redis.opsForValue().set(key, encoded, Duration.ofSeconds(120)); return null; });
        } catch (Exception e) { cacheFailure(e); }
    }
    private void cacheFailure(Exception e) {
        if (!(e instanceof RedisCircuitBreaker.OpenException))
            metrics.counter("blindbox.catalog.cache", "outcome", "redis_error").increment();
    }

    /** Fixed stripes bound memory; contending misses wait at most 50ms without borrowing JDBC connections. */
    public Map<String, Object> getOrLoad(String suffix, Supplier<Map<String, Object>> loader) {
        String key = key(suffix);
        Map<String, Object> cached = get(key);
        if (cached != null) {
            metrics.counter("blindbox.catalog.cache", "outcome", "hit").increment();
            return cached;
        }
        metrics.counter("blindbox.catalog.cache", "outcome", "miss").increment();
        ReentrantLock lock = loads[Math.floorMod(suffix.hashCode(), loads.length)];
        boolean acquired;
        try { acquired = lock.tryLock(50, TimeUnit.MILLISECONDS); }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(503, "商品目录读取已中断，请稍后重试");
        }
        if (!acquired) {
            cached = get(key);
            if (cached != null) return cached;
            metrics.counter("blindbox.catalog.cache", "outcome", "busy").increment();
            throw new BusinessException(503, "商品目录繁忙，请稍后重试");
        }
        try {
            cached = get(key);
            if (cached != null) return cached;
            Map<String, Object> loaded = loader.get();
            metrics.counter("blindbox.catalog.loads").increment();
            put(key, loaded);
            return loaded;
        } finally { lock.unlock(); }
    }

    /** Only metadata changes invalidate; successful draws and restocks do not touch this cache. */
    public void invalidateAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { enqueue(); }
            });
        } else enqueue();
    }

    private void enqueue() {
        dirty.set(true);
        if (!scheduled.compareAndSet(false, true)) return;
        try {
            invalidations.execute(() -> {
                dirty.set(false);
                try {
                    circuit.execute(() -> redis.opsForValue().increment(GENERATION));
                    metrics.counter("blindbox.catalog.invalidations", "outcome", "success").increment();
                } catch (Exception e) {
                    cacheFailure(e);
                    metrics.counter("blindbox.catalog.invalidations", "outcome", "error").increment();
                    log.warn("Catalog metadata invalidation unavailable; TTL bounds staleness to 120 seconds");
                } finally {
                    scheduled.set(false);
                    if (dirty.get()) enqueue();
                }
            });
        } catch (RejectedExecutionException e) {
            scheduled.set(false); dirty.set(false);
            metrics.counter("blindbox.catalog.invalidations", "outcome", "rejected").increment();
            log.warn("Catalog invalidation queue unavailable; TTL remains the fallback");
        }
    }
}
