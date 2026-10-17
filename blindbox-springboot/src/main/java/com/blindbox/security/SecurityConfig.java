package com.blindbox.security;

import com.blindbox.common.Result;
import io.micrometer.core.instrument.MeterRegistry;
import com.blindbox.core.PlatformMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;
import java.util.Arrays;
import java.util.List;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() {return new BCryptPasswordEncoder();}
    @Bean SecurityFilterChain security(HttpSecurity http,JwtService jwt,PlatformMapper db,ObjectMapper json,
        StringRedisTemplate redis, MeterRegistry metrics,
        @Value("${app.rate-limit-enabled:true}") boolean limited,
        @Value("${app.http-max-inflight:64}") int maxActive,
        @Value("${app.entry-read-per-minute:6000}") int readLimit,
        @Value("${app.entry-write-per-minute:120}") int writeLimit,
        @Value("${app.entry-auth-per-minute:20}") int authLimit) throws Exception {
        IngressFilter ingress=new IngressFilter(redis,json,limited,metrics,maxActive,readLimit,writeLimit,authLimit);
        JwtFilter jwtFilter=new JwtFilter(jwt,db,json);
        RateLimitFilter limitFilter=new RateLimitFilter(redis,json,limited);
        return http.csrf(c->c.disable()).cors(c->{})
            .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a->a
                .requestMatchers(HttpMethod.OPTIONS,"/**").permitAll()
                .requestMatchers("/actuator/health","/uploads/**").permitAll()
                .requestMatchers("/actuator/metrics","/actuator/metrics/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST,"/api/auth/login","/api/auth/register").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/blindboxes","/api/blindboxes/*","/api/playershows","/api/playershows/*/comments").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/**").authenticated().anyRequest().denyAll())
            .exceptionHandling(e->e.authenticationEntryPoint((req,res,err)->{
                res.setStatus(401);res.setContentType("application/json;charset=UTF-8");json.writeValue(res.getWriter(),Result.error(401,"请先登录"));
            }).accessDeniedHandler((req,res,err)->{
                res.setStatus(403);res.setContentType("application/json;charset=UTF-8");json.writeValue(res.getWriter(),Result.error(403,"没有操作权限"));
            }))
            .addFilterBefore(jwtFilter,UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(ingress,JwtFilter.class)
            .addFilterAfter(limitFilter,JwtFilter.class).build();
    }
    @Bean CorsConfigurationSource cors(@Value("${app.cors-origins:http://localhost:5173}") String origins) {
        CorsConfiguration c=new CorsConfiguration();c.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).toList());
        c.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        c.setAllowedHeaders(List.of("Authorization","Content-Type","Idempotency-Key"));
        c.setExposedHeaders(List.of("Retry-After"));c.setAllowCredentials(false);c.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source=new UrlBasedCorsConfigurationSource();source.registerCorsConfiguration("/api/**",c);return source;
    }
}
