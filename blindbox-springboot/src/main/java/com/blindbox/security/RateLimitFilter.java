package com.blindbox.security;

import com.blindbox.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/** Per-user quota; authentication endpoints use the preceding IP limiter. */
public class RateLimitFilter extends OncePerRequestFilter {
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final boolean enabled;
    public RateLimitFilter(StringRedisTemplate redis, ObjectMapper json, boolean enabled) {
        this.redis = redis; this.json = json; this.enabled = enabled;
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String path = request.getServletPath().isEmpty() ? request.getRequestURI() : request.getServletPath();
        if (!enabled || !path.startsWith("/api/") || path.startsWith("/api/auth/")
            || "GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()) || "OPTIONS".equals(request.getMethod())) {
            chain.doFilter(request, response); return;
        }
        Object identity = SecurityContextHolder.getContext().getAuthentication() == null ? null :
            SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(identity instanceof Principal principal)) { chain.doFilter(request, response); return; }
        String scope = path.endsWith("/draw") ? "draw" : path.endsWith("/recharge") ? "recharge" :
            path.equals("/api/images") ? "upload" : "write";
        int limit = switch (scope) { case "draw" -> 10; case "recharge" -> 5; case "upload" -> 6; default -> 30; };
        long count;
        try { count = RedisWindow.count(redis, "blindbox:limit:" + scope + ":user:" + principal.id(), limit); }
        catch (Exception e) { error(response, 503, "限流服务暂不可用，请稍后重试", 1); return; }
        if (count > limit) { error(response, 429, "操作过于频繁，请稍后重试", 60); return; }
        chain.doFilter(request, response);
    }
    private void error(HttpServletResponse response, int status, String message, int retry) throws IOException {
        response.setStatus(status); response.setHeader("Retry-After", Integer.toString(retry));
        response.setContentType("application/json;charset=UTF-8");
        json.writeValue(response.getWriter(), Result.error(status, message));
    }
}
