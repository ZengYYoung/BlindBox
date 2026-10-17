package com.blindbox.core;

import com.blindbox.common.BusinessException;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Per-instance bounded admission; MySQL locks remain the cross-instance correctness boundary. */
@Component
public class DrawAdmission {
    private final int maxGlobal;
    private final int maxPerPool;
    private final Set<Integer> users = new HashSet<>();
    private final Map<Integer, Integer> pools = new HashMap<>();
    private final MeterRegistry metrics;
    private int active;

    public DrawAdmission(@Value("${app.draw-max-inflight:8}") int maxGlobal,
                         @Value("${app.draw-max-per-pool:1}") int maxPerPool,
                         @Value("${spring.datasource.hikari.maximum-pool-size:16}") int connections,
                         MeterRegistry metrics) {
        if (maxGlobal < 1 || maxPerPool < 1 || connections < 1) throw new IllegalArgumentException("Invalid admission limits");
        this.maxGlobal = Math.min(maxGlobal, Math.max(1, connections / 2));
        this.maxPerPool = Math.min(maxPerPool, this.maxGlobal);
        this.metrics = metrics;
        metrics.gauge("blindbox.draw.active", this, gate -> gate.activeCount());
        metrics.gauge("blindbox.draw.limit", this.maxGlobal);
    }

    public synchronized Lease acquire(int userId, int poolId) {
        if (userId < 1 || poolId < 1) throw new BusinessException(400, "用户或盲盒 ID 不正确");
        if (users.contains(userId)) throw reject("user", "该账号已有抽奖正在处理，请保留原请求 ID");
        if (pools.getOrDefault(poolId, 0) >= maxPerPool) throw reject("pool", "该奖池繁忙，请使用原请求 ID 稍后重试");
        if (active >= maxGlobal) throw reject("global", "抽奖服务繁忙，请使用原请求 ID 稍后重试");
        users.add(userId);
        pools.merge(poolId, 1, Integer::sum);
        active++;
        return new Lease(userId, poolId);
    }

    private BusinessException reject(String reason, String message) {
        metrics.counter("blindbox.draw.admission.rejected", "reason", reason).increment();
        return new BusinessException(503, message);
    }

    public synchronized int activeCount() { return active; }

    public final class Lease implements AutoCloseable {
        private final int userId;
        private final int poolId;
        private final AtomicBoolean released = new AtomicBoolean();
        private Lease(int userId, int poolId) { this.userId = userId; this.poolId = poolId; }
        @Override public void close() {
            if (!released.compareAndSet(false, true)) return;
            synchronized (DrawAdmission.this) {
                users.remove(userId);
                pools.computeIfPresent(poolId, (id, count) -> count == 1 ? null : count - 1);
                active--;
            }
        }
    }
}
