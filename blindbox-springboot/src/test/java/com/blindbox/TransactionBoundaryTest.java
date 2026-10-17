package com.blindbox;

import com.blindbox.core.*;
import com.blindbox.core.Models.*;
import com.blindbox.common.BusinessException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.interceptor.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.support.*;
import java.util.concurrent.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class TransactionBoundaryTest {
    static class CommitBarrier extends AbstractPlatformTransactionManager {
        final CountDownLatch reached=new CountDownLatch(1),release=new CountDownLatch(1);
        final boolean fail;
        CommitBarrier(boolean fail) {this.fail=fail;}
        @Override protected Object doGetTransaction() {return new Object();}
        @Override protected void doBegin(Object tx,org.springframework.transaction.TransactionDefinition definition) {}
        @Override protected void doRollback(DefaultTransactionStatus status) {}
        @Override protected void doCommit(DefaultTransactionStatus status) {
            reached.countDown();
            try {if(!release.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("Test timed out");}
            catch(InterruptedException e) {Thread.currentThread().interrupt();throw new IllegalStateException(e);}
            if(fail) throw new IllegalStateException("Commit failure");
        }
    }
    private void checkBoundary(boolean fail) throws Exception {
        PlatformMapper db=mock(PlatformMapper.class);
        User user=new User();user.setId(7);when(db.lockUser(7)).thenReturn(user);
        com.blindbox.core.Models.Order prior=new com.blindbox.core.Models.Order();prior.setBlindBoxId(1);
        when(db.findOrderByRequest(7,"request-001")).thenReturn(prior);
        CommitBarrier manager=new CommitBarrier(fail);
        ProxyFactory factory=new ProxyFactory(new DrawTransactionService(db,new InventoryLottery()));
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));
        DrawTransactionService proxy=(DrawTransactionService)factory.getProxy();
        var metrics=new SimpleMeterRegistry();DrawAdmission gate=new DrawAdmission(8,1,16,metrics);
        DrawService service=new DrawService(proxy,gate,metrics);
        ExecutorService worker=Executors.newSingleThreadExecutor();
        try {
            Future<com.blindbox.core.Models.Order> first=worker.submit(()->service.draw(7,1,"request-001"));
            assertTrue(manager.reached.await(5,TimeUnit.SECONDS));
            assertEquals(1,gate.activeCount());
            assertThrows(BusinessException.class,()->service.draw(8,1,"request-002"));
            verify(db,never()).lockUser(8);
            assertNull(metrics.find("blindbox.draw.results").tag("outcome","replayed").counter());
            manager.release.countDown();
            if(fail) assertThrows(ExecutionException.class,()->first.get(5,TimeUnit.SECONDS));
            else assertSame(prior,first.get(5,TimeUnit.SECONDS));
            assertEquals(0,gate.activeCount());
        } finally {manager.release.countDown();worker.shutdownNow();}
    }
    @Test void permitIsHeldThroughSpringProxyCommit() throws Exception {checkBoundary(false);}
    @Test void commitFailureStillReleasesAdmission() throws Exception {checkBoundary(true);}
}
