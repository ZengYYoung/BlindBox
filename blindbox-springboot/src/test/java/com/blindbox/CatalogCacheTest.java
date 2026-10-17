package com.blindbox;

import com.blindbox.core.CatalogCache;
import com.blindbox.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.core.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogCacheTest {
    StringRedisTemplate redis;
    ValueOperations<String,String> values;
    Map<String,String> store;
    Queue<Runnable> tasks;
    CatalogCache cache;
    @BeforeEach void setup() {
        redis=mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ValueOperations<String,String> ops=mock(ValueOperations.class);
        values=ops;store=new ConcurrentHashMap<>();store.put("blindbox:catalog:generation","42");tasks=new ArrayDeque<>();
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(inv->store.get(inv.getArgument(0)));
        doAnswer(inv->{store.put(inv.getArgument(0),inv.getArgument(1));return null;})
            .when(values).set(anyString(),anyString(),any(Duration.class));
        cache=new CatalogCache(redis,new ObjectMapper(),tasks::add,new SimpleMeterRegistry());
    }
    @AfterEach void cleanup() {
        if(TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
    }
    @Test void hotCacheDoesNotInvokeLoader() {
        AtomicInteger calls=new AtomicInteger();
        var first=cache.getOrLoad("same",()->{calls.incrementAndGet();return Map.of("name","海洋");});
        assertEquals(first,cache.getOrLoad("same",()->{throw new AssertionError("Database must not be called");}));
        assertEquals(1,calls.get());
    }
    @Test void concurrentMissHasOneLoaderAndBoundedWaiting() throws Exception {
        ExecutorService threads=Executors.newSingleThreadExecutor();
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        AtomicInteger calls=new AtomicInteger();
        try {
            Future<?> first=threads.submit(()->cache.getOrLoad("same",()->{
                calls.incrementAndGet();entered.countDown();
                try {if(!release.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("Test timed out");}
                catch(InterruptedException e) {Thread.currentThread().interrupt();throw new IllegalStateException(e);}
                return Map.of("name","海洋");
            }));
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            assertThrows(BusinessException.class,()->cache.getOrLoad("same",()->{calls.incrementAndGet();return Map.of();}));
            assertEquals(1,calls.get());
            release.countDown();first.get(5,TimeUnit.SECONDS);
            assertEquals("海洋",cache.getOrLoad("same",()->{throw new AssertionError("Loaded cache expected");}).get("name"));
        } finally {release.countDown();threads.shutdownNow();}
    }
    @Test void commitOnlyEnqueuesAndMultipleEventsCoalesce() {
        TransactionSynchronizationManager.initSynchronization();
        cache.invalidateAfterCommit();cache.invalidateAfterCommit();
        assertTrue(tasks.isEmpty());verify(values,never()).increment(anyString());
        var synchronizations=TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(s->s.afterCommit());
        assertEquals(1,tasks.size());verify(values,never()).increment(anyString());
        tasks.remove().run();verify(values).increment("blindbox:catalog:generation");
    }
    @Test void rollbackDoesNotScheduleInvalidation() {
        TransactionSynchronizationManager.initSynchronization();cache.invalidateAfterCommit();
        TransactionSynchronizationManager.getSynchronizations().forEach(s->s.afterCompletion(1));
        assertTrue(tasks.isEmpty());verify(values,never()).increment(anyString());
    }
    @Test void invalidationFailureAndRejectedQueueDoNotEscapeToCommittedCaller() {
        when(values.increment(anyString())).thenThrow(new RuntimeException("Redis unavailable"));
        cache.invalidateAfterCommit();assertDoesNotThrow(()->tasks.remove().run());
        CatalogCache full=new CatalogCache(redis,new ObjectMapper(),task->{throw new RejectedExecutionException();},new SimpleMeterRegistry());
        assertDoesNotThrow(full::invalidateAfterCommit);
    }
    @Test void redisFailureFallsBackAndCooldownAvoidsRepeatedNetworkCalls() {
        when(values.get(anyString())).thenThrow(new RuntimeException("Redis unavailable"));
        assertEquals("first",cache.getOrLoad("one",()->Map.of("name","first")).get("name"));
        assertEquals("second",cache.getOrLoad("two",()->Map.of("name","second")).get("name"));
        verify(values,times(1)).get("blindbox:catalog:generation");
    }
}
