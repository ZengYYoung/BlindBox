package com.blindbox;
import com.blindbox.security.*;
import com.blindbox.resilience.RedisCircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import jakarta.servlet.FilterChain;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class RateLimitTest {
    private RedisCircuitBreaker circuit() {return new RedisCircuitBreaker("limit-"+java.util.UUID.randomUUID(),1,Duration.ofSeconds(1),new SimpleMeterRegistry());}
    @AfterEach void clear() {SecurityContextHolder.clearContext();}
    @Test void redisFailureFailsClosedForAuthenticatedWrites() throws Exception {
        StringRedisTemplate redis=mock(StringRedisTemplate.class);FilterChain chain=mock(FilterChain.class);
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class),anyList(),any(Object[].class))).thenThrow(new RuntimeException());
        var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(new Principal(7,"test","USER"),null,java.util.List.of()));
        SecurityContextHolder.setContext(context);
        MockHttpServletRequest request=new MockHttpServletRequest("POST","/api/blindboxes/1/draw");
        MockHttpServletResponse response=new MockHttpServletResponse();
        new RateLimitFilter(redis,new ObjectMapper(),true,circuit()).doFilter(request,response,chain);
        assertEquals(503,response.getStatus());verifyNoInteractions(chain);
    }
    @Test void disabledDevelopmentLimitDoesNotCallRedis() throws Exception {
        StringRedisTemplate redis=mock(StringRedisTemplate.class);FilterChain chain=mock(FilterChain.class);
        MockHttpServletRequest request=new MockHttpServletRequest("POST","/api/auth/login");
        MockHttpServletResponse response=new MockHttpServletResponse();
        new RateLimitFilter(redis,new ObjectMapper(),false,circuit()).doFilter(request,response,chain);
        verify(chain).doFilter(request,response);verifyNoInteractions(redis);
    }
    @Test void unauthorizedWriteGoesToSecurityWithoutRedis() throws Exception {
        StringRedisTemplate redis=mock(StringRedisTemplate.class);FilterChain chain=mock(FilterChain.class);
        MockHttpServletRequest request=new MockHttpServletRequest("POST","/api/images");
        MockHttpServletResponse response=new MockHttpServletResponse();
        new RateLimitFilter(redis,new ObjectMapper(),true,circuit()).doFilter(request,response,chain);
        verify(chain).doFilter(request,response);verifyNoInteractions(redis);
    }
    @Test void ingressFailureOpensSharedLimiterCircuitAndUserWritesRemainClosed() throws Exception {
        StringRedisTemplate redis=mock(StringRedisTemplate.class);
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class),anyList(),any(Object[].class))).thenThrow(new RuntimeException());
        RedisCircuitBreaker shared=circuit();
        var ingress=new IngressFilter(redis,new ObjectMapper(),true,new SimpleMeterRegistry(),1,6000,120,20,shared);
        MockHttpServletResponse first=new MockHttpServletResponse();
        ingress.doFilter(new MockHttpServletRequest("POST","/api/auth/login"),first,mock(FilterChain.class));
        assertEquals(503,first.getStatus());
        var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(new Principal(7,"test","USER"),null,java.util.List.of()));
        SecurityContextHolder.setContext(context);
        FilterChain chain=mock(FilterChain.class);MockHttpServletResponse second=new MockHttpServletResponse();
        new RateLimitFilter(redis,new ObjectMapper(),true,shared).doFilter(new MockHttpServletRequest("POST","/api/blindboxes/1/draw"),second,chain);
        assertEquals(503,second.getStatus());verifyNoInteractions(chain);
        verify(redis,times(1)).execute(any(org.springframework.data.redis.core.script.RedisScript.class),anyList(),any(Object[].class));
    }
}
