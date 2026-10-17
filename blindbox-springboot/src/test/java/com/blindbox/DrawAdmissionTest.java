package com.blindbox;

import com.blindbox.common.BusinessException;
import com.blindbox.core.DrawAdmission;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class DrawAdmissionTest {
    @Test void globalPoolAndUserLimitsReleaseExactlyOnce() {
        var metrics=new SimpleMeterRegistry();
        DrawAdmission gate=new DrawAdmission(2,1,16,metrics);
        var one=gate.acquire(1,1);
        assertEquals(503,assertThrows(BusinessException.class,()->gate.acquire(2,1)).getCode());
        assertThrows(BusinessException.class,()->gate.acquire(1,2));
        var two=gate.acquire(2,2);
        assertThrows(BusinessException.class,()->gate.acquire(3,3));
        one.close();one.close();
        try(var three=gate.acquire(3,3)) {assertEquals(2,gate.activeCount());}
        two.close();assertEquals(0,gate.activeCount());
        assertEquals(1,metrics.get("blindbox.draw.admission.rejected").tag("reason","pool").counter().count());
    }
    @Test void fiftySimultaneousContendersEnterOnlyOneHotPool() throws Exception {
        DrawAdmission gate=new DrawAdmission(8,1,16,new SimpleMeterRegistry());
        ExecutorService threads=Executors.newFixedThreadPool(50);
        CountDownLatch start=new CountDownLatch(1),attempted=new CountDownLatch(50),release=new CountDownLatch(1);
        AtomicInteger admitted=new AtomicInteger(),rejected=new AtomicInteger();
        List<Future<?>> jobs=new ArrayList<>();
        try {
            for(int i=1;i<=50;i++) {
                int user=i;
                jobs.add(threads.submit(()->{
                    try {
                        start.await();
                        try(var permit=gate.acquire(user,1)) {
                            admitted.incrementAndGet();attempted.countDown();
                            if(!release.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("Test release timed out");
                        } catch(BusinessException e) {rejected.incrementAndGet();attempted.countDown();}
                    } catch(InterruptedException e) {Thread.currentThread().interrupt();throw new IllegalStateException(e);}
                }));
            }
            start.countDown();assertTrue(attempted.await(5,TimeUnit.SECONDS));
            assertEquals(1,admitted.get());assertEquals(49,rejected.get());assertEquals(1,gate.activeCount());
            release.countDown();
            for(Future<?> job:jobs) job.get(5,TimeUnit.SECONDS);
            assertEquals(0,gate.activeCount());
            try(var next=gate.acquire(1,1)) {assertEquals(1,gate.activeCount());}
        } finally {release.countDown();threads.shutdownNow();}
    }
    @Test void globalBudgetReservesHalfOfConnectionPoolAndExceptionsRelease() {
        DrawAdmission gate=new DrawAdmission(8,8,2,new SimpleMeterRegistry());
        assertThrows(IllegalStateException.class,()->{
            try(var permit=gate.acquire(1,1)) {
                assertThrows(BusinessException.class,()->gate.acquire(2,2));
                throw new IllegalStateException("Transaction failed");
            }
        });
        assertEquals(0,gate.activeCount());
        try(var permit=gate.acquire(2,2)) {assertEquals(1,gate.activeCount());}
    }
}
