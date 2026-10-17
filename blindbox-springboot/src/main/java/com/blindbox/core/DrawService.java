package com.blindbox.core;

import com.blindbox.common.BusinessException;
import com.blindbox.core.Models.Order;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;
import java.util.Map;

/** Admission runs outside the transaction; the separate bean is invoked through Spring's proxy. */
@Service
public class DrawService {
    private final DrawTransactionService transactions;
    private final DrawAdmission admission;
    private final MeterRegistry metrics;

    public DrawService(DrawTransactionService transactions, DrawAdmission admission, MeterRegistry metrics) {
        this.transactions = transactions;
        this.admission = admission;
        this.metrics = metrics;
    }

    public static String requestKey(String key) {
        if (key == null || !key.matches("[A-Za-z0-9_-]{8,64}"))
            throw new BusinessException(400, "Idempotency-Key 必须是 8 至 64 位字母、数字、下划线或连字符");
        return key;
    }

    public Order draw(int userId, int blindBoxId, String request) {
        requestKey(request);
        Timer.Sample sample = Timer.start(metrics);
        String outcome = "error";
        try (DrawAdmission.Lease ignored = admission.acquire(userId, blindBoxId)) {
            var result = transactions.draw(userId, blindBoxId, request);
            outcome = result.replayed() ? "replayed" : "committed";
            return result.order();
        } catch (BusinessException e) {
            outcome = e.getCode() == 503 ? "busy" : "rejected";
            throw e;
        } finally {
            metrics.counter("blindbox.draw.results", "outcome", outcome).increment();
            sample.stop(Timer.builder("blindbox.draw.duration").tag("outcome", outcome)
                .publishPercentileHistogram().register(metrics));
        }
    }

    public static void requireOne(int rows, String message) {
        if (rows != 1) throw new BusinessException(409, message);
    }

    public Map<String, Object> result(Order order) {
        return Map.of("orderId", order.getId(), "requestId", order.getRequestId(), "blindBoxId", order.getBlindBoxId(),
            "prizeName", order.getPrizeName(), "prizeImg", order.getPrizeImg(), "rarity", order.getRarity(), "price", order.getPrice());
    }
}
