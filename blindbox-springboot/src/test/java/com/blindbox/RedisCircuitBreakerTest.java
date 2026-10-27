package com.blindbox;
import com.blindbox.resilience.RedisCircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
class RedisCircuitBreakerTest {
    @Test void failedProbeReopensAndSuccessfulProbeCloses() {
        AtomicLong clock=new AtomicLong();SimpleMeterRegistry metrics=new SimpleMeterRegistry();
        var breaker=new RedisCircuitBreaker("test",2,Duration.ofSeconds(1),metrics,clock::get);
        assertThrows(IllegalStateException.class,()->breaker.execute(()->{throw new IllegalStateException();}));
        assertEquals("ok",breaker.execute(()->"ok"));
        for(int i=0;i<2;i++) assertThrows(IllegalStateException.class,()->breaker.execute(()->{throw new IllegalStateException();}));
        assertThrows(RedisCircuitBreaker.OpenException.class,()->breaker.execute(()->"must not run"));
        clock.addAndGet(Duration.ofSeconds(1).toNanos());
        assertThrows(IllegalStateException.class,()->breaker.execute(()->{throw new IllegalStateException();}));
        assertThrows(RedisCircuitBreaker.OpenException.class,()->breaker.execute(()->"must not run"));
        clock.addAndGet(Duration.ofSeconds(1).toNanos());
        assertEquals("recovered",breaker.execute(()->"recovered"));
        assertEquals("open",breaker.execute(()->"open"));
        assertEquals(2,metrics.counter("blindbox.redis.circuit.transitions","dependency","test","state","open").count());
        assertEquals(0,metrics.get("blindbox.redis.circuit.state").tag("dependency","test").gauge().value());
    }
    @Test void halfOpenAllowsOnlyOneProbeAndIgnoresLateOldSuccess() throws Exception {
        AtomicLong clock=new AtomicLong();
        var breaker=new RedisCircuitBreaker("test",1,Duration.ofSeconds(1),new SimpleMeterRegistry(),clock::get);
        ExecutorService workers=Executors.newFixedThreadPool(2);
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),probeEntered=new CountDownLatch(1),probeRelease=new CountDownLatch(1);
        try {
            Future<String> old=workers.submit(()->breaker.execute(()->{entered.countDown();await(release);return "old";}));
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class,()->breaker.execute(()->{throw new IllegalStateException();}));
            release.countDown();assertEquals("old",old.get(5,TimeUnit.SECONDS));
            assertThrows(RedisCircuitBreaker.OpenException.class,()->breaker.execute(()->"late success must not close"));
            clock.addAndGet(Duration.ofSeconds(1).toNanos());
            Future<String> probe=workers.submit(()->breaker.execute(()->{probeEntered.countDown();await(probeRelease);return "ok";}));
            assertTrue(probeEntered.await(5,TimeUnit.SECONDS));
            for(int i=0;i<20;i++) assertThrows(RedisCircuitBreaker.OpenException.class,()->breaker.execute(()->"unexpected"));
            probeRelease.countDown();assertEquals("ok",probe.get(5,TimeUnit.SECONDS));
            assertEquals("available",breaker.execute(()->"available"));
        } finally {release.countDown();probeRelease.countDown();workers.shutdownNow();}
    }
    private static void await(CountDownLatch latch) {
        try {if(!latch.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("timeout");}
        catch(InterruptedException e) {Thread.currentThread().interrupt();throw new IllegalStateException(e);}
    }
}
