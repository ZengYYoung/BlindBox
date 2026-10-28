package com.blindbox;
import com.blindbox.api.ApiController;
import com.blindbox.core.*;
import com.blindbox.core.Models.User;
import com.blindbox.security.*;
import com.blindbox.resilience.RedisCircuitConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value=ApiController.class,properties="app.rate-limit-enabled=false",excludeAutoConfiguration=org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class)
@Import({SecurityConfig.class,SecurityTest.MetricsConfig.class,RedisCircuitConfig.class})
class SecurityTest {
    @TestConfiguration static class MetricsConfig {
        @Bean MeterRegistry meterRegistry() { return new SimpleMeterRegistry(); }
    }
    @Autowired MockMvc mvc;
    @MockBean PlatformMapper db;@MockBean UserService users;@MockBean CatalogService catalog;
    @MockBean DrawService draws;@MockBean CommunityService community;@MockBean ImageService images;
    @MockBean com.blindbox.messaging.NotificationService notifications;
    @MockBean JwtService jwt;@MockBean StringRedisTemplate redis;
    @Test void anonymousCannotDrawOrUpload() throws Exception {
        mvc.perform(post("/api/blindboxes/1/draw")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/images")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/user/notifications")).andExpect(status().isUnauthorized());
    }
    @Test void notificationsUseAuthenticatedUserId() throws Exception {
        User user=new User();user.setId(7);user.setAccount("collector");user.setRole("USER");
        when(jwt.parse("test-token")).thenReturn(7);when(db.user(7)).thenReturn(user);
        mvc.perform(get("/api/user/notifications").header("Authorization","Bearer test-token"))
            .andExpect(status().isOk());
        verify(notifications).list(7);
    }
    @Test void regularUserCannotAdminister() throws Exception {
        User user=new User();user.setId(7);user.setAccount("collector");user.setRole("USER");
        when(jwt.parse("test-token")).thenReturn(7);when(db.user(7)).thenReturn(user);
        mvc.perform(post("/api/admin/blindboxes").header("Authorization","Bearer test-token").contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
    }
    @Test void publicCatalogRequiresNoToken() throws Exception {
        mvc.perform(get("/api/blindboxes")).andExpect(status().isOk());
    }
    @Test void metricsRequireAuthenticationAndAdminRole() throws Exception {
        mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
        User user=new User();user.setId(7);user.setAccount("collector");user.setRole("USER");
        when(jwt.parse("test-token")).thenReturn(7);when(db.user(7)).thenReturn(user);
        mvc.perform(get("/actuator/metrics").header("Authorization","Bearer test-token"))
            .andExpect(status().isForbidden());
    }
    @Test void publicCatalogDoesNotQueryUserEvenWithBearerToken() throws Exception {
        mvc.perform(get("/api/blindboxes").header("Authorization","Bearer expired-token"))
            .andExpect(status().isOk());
        verifyNoInteractions(jwt);verify(db,never()).user(anyInt());
    }
    @Test void loginDoesNotParseStaleBearerToken() throws Exception {
        when(users.login(any())).thenReturn(java.util.Map.of("token","new-token"));
        mvc.perform(post("/api/auth/login").header("Authorization","Bearer expired-token")
            .contentType("application/json").content("{\"account\":\"collector\",\"password\":\"password-123\"}"))
            .andExpect(status().isOk());
        verifyNoInteractions(jwt);verify(db,never()).user(anyInt());
    }
    @Test void validationReturns400ForBadCredentials() throws Exception {
        mvc.perform(post("/api/auth/register").contentType("application/json").content("{\"account\":\"a\",\"password\":\"x\"}"))
            .andExpect(status().isBadRequest());
    }
    @Test void transactionAdmissionOrConnectionFailureReturnsRetryable503() throws Exception {
        User user=new User();user.setId(7);user.setAccount("collector");user.setRole("USER");
        when(jwt.parse("test-token")).thenReturn(7);when(db.user(7)).thenReturn(user);
        when(draws.draw(7,1,"request-001"))
            .thenThrow(new com.blindbox.common.BusinessException(503,"奖池繁忙"))
            .thenThrow(new org.springframework.transaction.CannotCreateTransactionException("Connection budget exhausted"));
        for(int attempt=0;attempt<2;attempt++) {
            mvc.perform(post("/api/blindboxes/1/draw").header("Authorization","Bearer test-token")
                .header("Idempotency-Key","request-001"))
                .andExpect(status().isServiceUnavailable()).andExpect(header().string("Retry-After","1"))
                .andExpect(jsonPath("$.code").value(503));
        }
    }
    @Test void authenticationDatabaseFailureReturnsRetryable503() throws Exception {
        when(jwt.parse("test-token")).thenReturn(7);
        when(db.user(7)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("Database unavailable"));
        mvc.perform(get("/api/user/info").header("Authorization","Bearer test-token"))
            .andExpect(status().isServiceUnavailable()).andExpect(header().string("Retry-After","1"));
        verifyNoInteractions(users);
    }
    @Test void drawConflictsExposeSafeOutcomeOnlyWhenServerCanConfirmIt() throws Exception {
        User user=new User();user.setId(7);user.setAccount("collector");user.setRole("USER");
        when(jwt.parse("test-token")).thenReturn(7);when(db.user(7)).thenReturn(user);
        when(draws.draw(7,1,"request-001"))
            .thenThrow(new com.blindbox.common.BusinessException(409,"库存已售罄","DRAW_SOLD_OUT"))
            .thenThrow(new org.springframework.dao.DuplicateKeyException("duplicate"))
            .thenThrow(new com.blindbox.common.BusinessException(409,"库存校验失败"));
        mvc.perform(post("/api/blindboxes/1/draw").header("Authorization","Bearer test-token").header("Idempotency-Key","request-001"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("DRAW_SOLD_OUT"));
        mvc.perform(post("/api/blindboxes/1/draw").header("Authorization","Bearer test-token").header("Idempotency-Key","request-001"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("REQUEST_CONFLICT"));
        mvc.perform(post("/api/blindboxes/1/draw").header("Authorization","Bearer test-token").header("Idempotency-Key","request-001"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").doesNotExist());
    }
}
