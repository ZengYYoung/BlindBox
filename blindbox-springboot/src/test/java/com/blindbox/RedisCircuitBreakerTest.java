package com.blindbox;
import com.blindbox.resilience.RedisCircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class RedisCircuitBreakerTest {
    @Test void sentinelCountsErrorsWithinWindowAndProbesRecovery() throws Exception {
        String name="test-"+UUID.randomUUID();SimpleMeterRegistry metrics=new SimpleMeterRegistry();
        try(var breaker=new RedisCircuitBreaker(name,2,Duration.ofSeconds(1),metrics)) {
            assertThrows(IllegalStateException.class,()->breaker.execute(()->{throw new IllegalStateException();}));
            assertEquals("ok",breaker.execute(()->"ok"));
            assertThrows(IllegalStateException.class,()->breaker.execute(()->{throw new IllegalStateException();}));
            assertThrows(RedisCircuitBreaker.OpenException.class,()->breaker.execute(()->"must not run"));
            Thread.sleep(1200);
            assertThrows(IllegalStateException.class,()->breaker.execute(()->{throw new IllegalStateException();}));
            assertThrows(RedisCircuitBreaker.OpenException.class,()->breaker.execute(()->"must not run"));
            Thread.sleep(1200);
            assertEquals("recovered",breaker.execute(()->"recovered"));
            assertEquals("open",breaker.execute(()->"open"));
            assertEquals(2,metrics.counter("blindbox.redis.circuit.transitions","dependency",name,"state","open").count());
            assertEquals(0,metrics.get("blindbox.redis.circuit.state").tag("dependency",name).gauge().value());
        }
    }
    @Test void sentinelHalfOpenAllowsOnlyOneProbe() throws Exception {
        String name="test-"+UUID.randomUUID();
        try(var breaker=new RedisCircuitBreaker(name,1,Duration.ofSeconds(1),new SimpleMeterRegistry())) {
            ExecutorService worker=Executors.newSingleThreadExecutor();
            CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
            try {
                assertThrows(IllegalStateException.class,()->breaker.execute(()->{throw new IllegalStateException();}));
                assertThrows(RedisCircuitBreaker.OpenException.class,()->breaker.execute(()->"blocked"));
                Thread.sleep(1200);
                Future<String> probe=worker.submit(()->breaker.execute(()->{entered.countDown();await(release);return "ok";}));
                assertTrue(entered.await(5,TimeUnit.SECONDS));
                for(int i=0;i<20;i++) assertThrows(RedisCircuitBreaker.OpenException.class,()->breaker.execute(()->"unexpected"));
                release.countDown();assertEquals("ok",probe.get(5,TimeUnit.SECONDS));
                assertEquals("available",breaker.execute(()->"available"));
            } finally {release.countDown();worker.shutdownNow();}
        }
    }
    @Test void waitsForOldInflightCallsBeforeHalfOpenProbe() throws Exception {
        String name="test-"+UUID.randomUUID();
        try(var breaker=new RedisCircuitBreaker(name,1,Duration.ofSeconds(1),new SimpleMeterRegistry())) {
            ExecutorService worker=Executors.newSingleThreadExecutor();
            CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
            try {
                Future<String> old=worker.submit(()->breaker.execute(()->{entered.countDown();await(release);return "old";}));
                assertTrue(entered.await(5,TimeUnit.SECONDS));
                assertThrows(IllegalStateException.class,()->breaker.execute(()->{throw new IllegalStateException();}));
                Thread.sleep(1200);
                assertThrows(RedisCircuitBreaker.OpenException.class,()->breaker.execute(()->"must wait"));
                release.countDown();assertEquals("old",old.get(5,TimeUnit.SECONDS));
                assertEquals("probe",breaker.execute(()->"probe"));
            } finally {release.countDown();worker.shutdownNow();}
        }
    }
    @Test void identicalRuleCanBeRegisteredTwiceAndSharesSentinelState() {
        String name="test-"+UUID.randomUUID();
        try(var first=new RedisCircuitBreaker(name,1,Duration.ofSeconds(1),new SimpleMeterRegistry());
            var second=new RedisCircuitBreaker(name,1,Duration.ofSeconds(1),new SimpleMeterRegistry())) {
            assertThrows(IllegalStateException.class,()->first.execute(()->{throw new IllegalStateException();}));
            assertThrows(RedisCircuitBreaker.OpenException.class,()->second.execute(()->"should be blocked"));
        }
    }
    private static void await(CountDownLatch latch) {
        try {if(!latch.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("timeout");}
        catch(InterruptedException e) {Thread.currentThread().interrupt();throw new IllegalStateException(e);}
    }
}
