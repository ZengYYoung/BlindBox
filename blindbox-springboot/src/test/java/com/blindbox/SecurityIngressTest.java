package com.blindbox;
import com.blindbox.api.ApiController;
import com.blindbox.core.*;
import com.blindbox.security.*;
import com.blindbox.resilience.RedisCircuitConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value=ApiController.class,properties={"app.rate-limit-enabled=true","app.redis-circuit-failure-threshold=100"},
 excludeAutoConfiguration=org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class)
@Import({SecurityConfig.class,SecurityTest.MetricsConfig.class,RedisCircuitConfig.class})
class SecurityIngressTest {
    @Autowired MockMvc mvc;
    @MockBean PlatformMapper db;@MockBean UserService users;@MockBean CatalogService catalog;
    @MockBean DrawService draws;@MockBean CommunityService community;@MockBean ImageService images;
    @MockBean com.blindbox.messaging.NotificationService notifications;
    @MockBean JwtService jwt;@MockBean StringRedisTemplate redis;
    @Test void overloadedAuthenticatedWriteNeverParsesJwtOrBorrowsDatabase() throws Exception {
        when(redis.execute(any(RedisScript.class),anyList(),any(Object[].class))).thenReturn(121L);
        mvc.perform(post("/api/blindboxes/1/draw").header("Authorization","Bearer test-token"))
            .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","60"));
        verifyNoInteractions(jwt,db,draws);
    }
    @Test void loginIpLimitCannotBeBypassedWithBearerHeader() throws Exception {
        when(redis.execute(any(RedisScript.class),anyList(),any(Object[].class))).thenReturn(21L);
        mvc.perform(post("/api/auth/login").header("Authorization","Bearer test-token"))
            .andExpect(status().isTooManyRequests());
        verifyNoInteractions(jwt,db,users);
    }
    @Test void readCanReachCatalogWhenRedisIsUnavailable() throws Exception {
        when(redis.execute(any(RedisScript.class),anyList(),any(Object[].class))).thenThrow(new RuntimeException());
        when(catalog.list(1,12,"","",false)).thenReturn(java.util.Map.of("list",java.util.List.of()));
        mvc.perform(get("/api/blindboxes")).andExpect(status().isOk());
        verify(catalog).list(1,12,"","",false);verifyNoInteractions(db);
    }
}
