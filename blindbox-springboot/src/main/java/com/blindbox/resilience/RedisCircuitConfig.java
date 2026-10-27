package com.blindbox.resilience;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Duration;

@Configuration
public class RedisCircuitConfig {
    @Bean("redisCacheCircuit")
    RedisCircuitBreaker cacheCircuit(MeterRegistry registry,
        @Value("${app.redis-circuit-failure-threshold:1}") int threshold,
        @Value("${app.redis-circuit-open-ms:1000}") long openMillis) {
        return new RedisCircuitBreaker("cache", threshold, Duration.ofMillis(openMillis), registry);
    }
    @Bean("redisLimitCircuit")
    RedisCircuitBreaker limitCircuit(MeterRegistry registry,
        @Value("${app.redis-circuit-failure-threshold:1}") int threshold,
        @Value("${app.redis-circuit-open-ms:1000}") long openMillis) {
        return new RedisCircuitBreaker("limit", threshold, Duration.ofMillis(openMillis), registry);
    }
}
