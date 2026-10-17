package com.blindbox;
import com.blindbox.security.*;
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
    @AfterEach void clear() {SecurityContextHolder.clearContext();}
    @Test void redisFailureFailsClosedForAuthenticatedWrites() throws Exception {
        StringRedisTemplate redis=mock(StringRedisTemplate.class);FilterChain chain=mock(FilterChain.class);
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class),anyList(),any(Object[].class))).thenThrow(new RuntimeException());
        var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(new Principal(7,"test","USER"),null,java.util.List.of()));
        SecurityContextHolder.setContext(context);
        MockHttpServletRequest request=new MockHttpServletRequest("POST","/api/blindboxes/1/draw");
        MockHttpServletResponse response=new MockHttpServletResponse();
        new RateLimitFilter(redis,new ObjectMapper(),true).doFilter(request,response,chain);
        assertEquals(503,response.getStatus());verifyNoInteractions(chain);
    }
    @Test void disabledDevelopmentLimitDoesNotCallRedis() throws Exception {
        StringRedisTemplate redis=mock(StringRedisTemplate.class);FilterChain chain=mock(FilterChain.class);
        MockHttpServletRequest request=new MockHttpServletRequest("POST","/api/auth/login");
        MockHttpServletResponse response=new MockHttpServletResponse();
        new RateLimitFilter(redis,new ObjectMapper(),false).doFilter(request,response,chain);
        verify(chain).doFilter(request,response);verifyNoInteractions(redis);
    }
    @Test void unauthorizedWriteGoesToSecurityWithoutRedis() throws Exception {
        StringRedisTemplate redis=mock(StringRedisTemplate.class);FilterChain chain=mock(FilterChain.class);
        MockHttpServletRequest request=new MockHttpServletRequest("POST","/api/images");
        MockHttpServletResponse response=new MockHttpServletResponse();
        new RateLimitFilter(redis,new ObjectMapper(),true).doFilter(request,response,chain);
        verify(chain).doFilter(request,response);verifyNoInteractions(redis);
    }
}
