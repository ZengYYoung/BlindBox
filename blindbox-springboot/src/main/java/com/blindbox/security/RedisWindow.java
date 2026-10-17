package com.blindbox.security;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import java.util.List;

/** Saturated counters and TTL repair keep rejected traffic bounded. */
final class RedisWindow {
    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>(
        "local n=tonumber(redis.call('GET',KEYS[1]) or '0'); "
        + "if n < tonumber(ARGV[2])+1 then n=redis.call('INCR',KEYS[1]); end; "
        + "if redis.call('TTL',KEYS[1]) < 0 then redis.call('EXPIRE',KEYS[1],ARGV[1]); end; return n", Long.class);
    private RedisWindow() {}
    static long count(StringRedisTemplate redis, String key, int limit) {
        Long value = redis.execute(SCRIPT, List.of(key), "60", Integer.toString(limit));
        if (value == null) throw new IllegalStateException("Rate limiter returned no result");
        return value;
    }
}
