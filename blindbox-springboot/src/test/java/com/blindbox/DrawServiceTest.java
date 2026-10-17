package com.blindbox;

import com.blindbox.core.*;
import com.blindbox.core.Models.Order;
import com.blindbox.common.BusinessException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DrawServiceTest {
    @Test void rejectionOccursBeforeTransactionBeanIsCalled() {
        var metrics=new SimpleMeterRegistry();
        DrawAdmission gate=new DrawAdmission(8,1,16,metrics);
        DrawTransactionService transactions=mock(DrawTransactionService.class);
        DrawService service=new DrawService(transactions,gate,metrics);
        try(var held=gate.acquire(7,1)) {
            assertThrows(BusinessException.class,()->service.draw(8,1,"request-001"));
            verifyNoInteractions(transactions);
        }
        assertEquals(0,gate.activeCount());
    }
    @Test void successReplayAndTransactionFailureReleasePermitsAndUseSeparateMetrics() {
        var metrics=new SimpleMeterRegistry();
        DrawAdmission gate=new DrawAdmission(8,1,16,metrics);
        DrawTransactionService transactions=mock(DrawTransactionService.class);
        DrawService service=new DrawService(transactions,gate,metrics);
        Order order=new Order();
        when(transactions.draw(7,1,"request-001")).thenReturn(new DrawTransactionService.DrawResult(order,false));
        assertSame(order,service.draw(7,1,"request-001"));assertEquals(0,gate.activeCount());
        when(transactions.draw(7,1,"request-001")).thenReturn(new DrawTransactionService.DrawResult(order,true));
        service.draw(7,1,"request-001");
        when(transactions.draw(7,1,"request-001")).thenThrow(new IllegalStateException("Commit failed"));
        assertThrows(IllegalStateException.class,()->service.draw(7,1,"request-001"));
        assertEquals(0,gate.activeCount());
        assertEquals(1,metrics.get("blindbox.draw.results").tag("outcome","committed").counter().count());
        assertEquals(1,metrics.get("blindbox.draw.results").tag("outcome","replayed").counter().count());
        assertEquals(1,metrics.get("blindbox.draw.results").tag("outcome","error").counter().count());
    }
}
