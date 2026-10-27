package com.blindbox.security;

import com.blindbox.common.Result;
import com.blindbox.resilience.RedisCircuitBreaker;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/** Runs before JWT database lookup. Client forwarding headers are not trusted. */
public class IngressFilter extends OncePerRequestFilter {
    private final StringRedisTemplate redis;
    private final RedisCircuitBreaker circuit;
    private final ObjectMapper json;
    private final boolean limited;
    private final MeterRegistry metrics;
    private final Semaphore permits;
    private final AtomicInteger active = new AtomicInteger();
    private final int readLimit, writeLimit, authLimit;

    public IngressFilter(StringRedisTemplate redis, ObjectMapper json, boolean limited, MeterRegistry metrics,
                         int maxActive, int readLimit, int writeLimit, int authLimit, RedisCircuitBreaker circuit) {
        if (maxActive < 1 || readLimit < 1 || writeLimit < 1 || authLimit < 1)
            throw new IllegalArgumentException("Invalid ingress limits");
        this.redis = redis; this.circuit = circuit; this.json = json; this.limited = limited; this.metrics = metrics;
        permits = new Semaphore(maxActive);
        this.readLimit = readLimit; this.writeLimit = writeLimit; this.authLimit = authLimit;
        metrics.gauge("blindbox.http.active", active);
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String path = request.getServletPath().isEmpty() ? request.getRequestURI() : request.getServletPath();
        if (!path.startsWith("/api/") || "OPTIONS".equals(request.getMethod())) {
            chain.doFilter(request, response); return;
        }
        if (!permits.tryAcquire()) {
            reject(response, 503, "concurrency", "请求过多，请稍后重试", 1); return;
        }
        active.incrementAndGet();
        try {
            boolean read = "GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod());
            String scope = path.equals("/api/auth/login") || path.equals("/api/auth/register")
                ? "auth" : read ? "read" : "write";
            int limit = switch (scope) { case "auth" -> authLimit; case "read" -> readLimit; default -> writeLimit; };
            if (limited) {
                long count;
                try {
                    count = circuit.execute(() -> RedisWindow.count(redis, "blindbox:limit:entry:" + scope + ":" + hash(request.getRemoteAddr()), limit));
                } catch (Exception e) {
                    if (!(e instanceof RedisCircuitBreaker.OpenException))
                        metrics.counter("blindbox.http.entry.redis_errors", "scope", scope).increment();
                    if (!read) { reject(response, 503, "redis", "入口限流服务暂不可用，请稍后重试", 1); return; }
                    count = 0; // Read availability remains protected by local concurrency admission.
                }
                if (count > limit) { reject(response, 429, "rate", "该来源请求过于频繁，请稍后重试", 60); return; }
            }
            chain.doFilter(request, response);
        } finally {
            active.decrementAndGet(); permits.release();
        }
    }
    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private void reject(HttpServletResponse response, int status, String reason, String text, int retryAfter) throws IOException {
        metrics.counter("blindbox.http.entry.rejected", "reason", reason).increment();
        response.setStatus(status); response.setHeader("Retry-After", Integer.toString(retryAfter));
        response.setContentType("application/json;charset=UTF-8");
        json.writeValue(response.getWriter(), Result.error(status, text));
    }
}
