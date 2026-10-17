package com.blindbox;

import com.blindbox.security.IngressFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.web.*;
import jakarta.servlet.FilterChain;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IngressFilterTest {
    @Test void ipQuotaRejectsBeforeJwtOrDatabaseChain() throws Exception {
        StringRedisTemplate redis=mock(StringRedisTemplate.class);FilterChain chain=mock(FilterChain.class);
        when(redis.execute(any(RedisScript.class),anyList(),any(Object[].class))).thenReturn(21L);
        IngressFilter filter=new IngressFilter(redis,new ObjectMapper(),true,new SimpleMeterRegistry(),64,6000,120,20);
        MockHttpServletResponse response=new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("POST","/api/auth/login"),response,chain);
        assertEquals(429,response.getStatus());assertEquals("60",response.getHeader("Retry-After"));
        verifyNoInteractions(chain);
    }
    @Test void redisFailureAllowsBoundedReadsButRejectsWrites() throws Exception {
        StringRedisTemplate redis=mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class),anyList(),any(Object[].class))).thenThrow(new RuntimeException());
        IngressFilter filter=new IngressFilter(redis,new ObjectMapper(),true,new SimpleMeterRegistry(),1,6000,120,20);
        FilterChain read=mock(FilterChain.class);
        filter.doFilter(new MockHttpServletRequest("GET","/api/blindboxes"),new MockHttpServletResponse(),read);
        verify(read).doFilter(any(),any());
        FilterChain write=mock(FilterChain.class);MockHttpServletResponse rejected=new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("POST","/api/auth/login"),rejected,write);
        assertEquals(503,rejected.getStatus());verifyNoInteractions(write);
        FilterChain next=mock(FilterChain.class);
        filter.doFilter(new MockHttpServletRequest("GET","/api/blindboxes"),new MockHttpServletResponse(),next);
        verify(next).doFilter(any(),any());
    }
    @Test void slowRequestCannotExhaustAllSlotsAndPermitReturnsAfterFailure() throws Exception {
        StringRedisTemplate redis=mock(StringRedisTemplate.class);
        IngressFilter filter=new IngressFilter(redis,new ObjectMapper(),false,new SimpleMeterRegistry(),1,6000,120,20);
        ExecutorService worker=Executors.newSingleThreadExecutor();
        CountDownLatch inside=new CountDownLatch(1),release=new CountDownLatch(1);
        try {
            Future<?> first=worker.submit(()->{
                try {
                    filter.doFilter(new MockHttpServletRequest("GET","/api/blindboxes"),new MockHttpServletResponse(),(req,res)->{
                        inside.countDown();
                        try {if(!release.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("Test timed out");}
                        catch(InterruptedException e) {Thread.currentThread().interrupt();throw new IllegalStateException(e);}
                        throw new IllegalStateException("Handler failed");
                    });
                } catch(Exception e) {throw new IllegalStateException(e);}
            });
            assertTrue(inside.await(5,TimeUnit.SECONDS));
            MockHttpServletResponse response=new MockHttpServletResponse();FilterChain blocked=mock(FilterChain.class);
            filter.doFilter(new MockHttpServletRequest("GET","/api/blindboxes"),response,blocked);
            assertEquals(503,response.getStatus());verifyNoInteractions(blocked);verifyNoInteractions(redis);
            release.countDown();assertThrows(ExecutionException.class,()->first.get(5,TimeUnit.SECONDS));
            FilterChain next=mock(FilterChain.class);
            filter.doFilter(new MockHttpServletRequest("GET","/api/blindboxes"),new MockHttpServletResponse(),next);
            verify(next).doFilter(any(),any());
        } finally {release.countDown();worker.shutdownNow();}
    }
}
