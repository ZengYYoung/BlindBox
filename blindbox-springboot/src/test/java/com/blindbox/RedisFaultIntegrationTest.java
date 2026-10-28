package com.blindbox;
import com.blindbox.core.CatalogCache;
import com.blindbox.resilience.RedisCircuitBreaker;
import com.blindbox.security.IngressFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="RUN_REDIS_FAULT_TEST", matches="YES")
class RedisFaultIntegrationTest {
    @TempDir Path temp;
    @Test void disposableRedisOutageFailsWritesClosedAndCircuitsRecover() throws Exception {
        int port;try (ServerSocket socket=new ServerSocket(0)) {port=socket.getLocalPort();}
        Process server=null;LettuceConnectionFactory factory=null;
        try {
            server=startRedis(port);
            factory=new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1",port),
                LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(500)).build());
            factory.afterPropertiesSet();factory.start();awaitRedis(factory);
            StringRedisTemplate redis=new StringRedisTemplate(factory);redis.afterPropertiesSet();
            var metrics=new SimpleMeterRegistry();
            var limit=new RedisCircuitBreaker("limit",1,Duration.ofSeconds(1),metrics);
            var cacheGate=new RedisCircuitBreaker("cache",1,Duration.ofSeconds(1),metrics);
            var ingress=new IngressFilter(redis,new ObjectMapper(),true,metrics,4,6000,120,20,limit);
            var cache=new CatalogCache(redis,new ObjectMapper(),Runnable::run,metrics,cacheGate);
            FilterChain first=mock(FilterChain.class);
            ingress.doFilter(new MockHttpServletRequest("GET","/api/blindboxes"),new MockHttpServletResponse(),first);
            verify(first).doFilter(any(),any());
            assertEquals("before",cache.getOrLoad("before",()->Map.of("name","before")).get("name"));
            server.destroy();assertTrue(server.waitFor(5,TimeUnit.SECONDS));server=null;
            MockHttpServletResponse write=new MockHttpServletResponse();FilterChain blocked=mock(FilterChain.class);
            ingress.doFilter(new MockHttpServletRequest("POST","/api/auth/login"),write,blocked);
            assertEquals(503,write.getStatus());verifyNoInteractions(blocked);
            assertEquals("fallback",cache.getOrLoad("outage",()->Map.of("name","fallback")).get("name"));
            MockHttpServletResponse fast=new MockHttpServletResponse();
            ingress.doFilter(new MockHttpServletRequest("POST","/api/auth/login"),fast,mock(FilterChain.class));
            assertEquals(503,fast.getStatus());
            assertEquals(1,metrics.counter("blindbox.redis.circuit.calls","dependency","limit","outcome","rejected").count());
            server=startRedis(port);awaitRedis(factory);Thread.sleep(1100);
            FilterChain recovered=mock(FilterChain.class);MockHttpServletResponse ok=new MockHttpServletResponse();
            ingress.doFilter(new MockHttpServletRequest("POST","/api/auth/login"),ok,recovered);
            verify(recovered).doFilter(any(),any());assertEquals(200,ok.getStatus());
            assertEquals("recovered",cache.getOrLoad("after",()->Map.of("name","recovered")).get("name"));
            assertEquals("recovered",cache.getOrLoad("after",()->{throw new AssertionError("cache miss");}).get("name"));
            assertEquals(0,metrics.get("blindbox.redis.circuit.state").tag("dependency","limit").gauge().value());
            assertEquals(0,metrics.get("blindbox.redis.circuit.state").tag("dependency","cache").gauge().value());
        } finally {
            if(factory!=null)factory.destroy();
            if(server!=null){server.destroy();if(!server.waitFor(5,TimeUnit.SECONDS))server.destroyForcibly();}
        }
    }
    private Process startRedis(int port) throws Exception {
        return new ProcessBuilder("redis-server","--bind","127.0.0.1","--port",Integer.toString(port),
            "--save","","--appendonly","no","--dir",temp.toString()).redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
    }
    private static void awaitRedis(LettuceConnectionFactory factory) throws Exception {
        for(int i=0;i<50;i++) {
            try(var connection=factory.getConnection()){if("PONG".equals(connection.ping()))return;}
            catch(Exception ignored){Thread.sleep(100);}
        }
        throw new IllegalStateException("Disposable Redis did not start");
    }
}
