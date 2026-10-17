package com.blindbox.security;

import com.blindbox.common.Result;
import com.blindbox.core.Models.User;
import com.blindbox.core.PlatformMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

public class JwtFilter extends OncePerRequestFilter {
    private final JwtService jwt;private final PlatformMapper db;private final ObjectMapper json;
    public JwtFilter(JwtService jwt,PlatformMapper db,ObjectMapper json) {this.jwt=jwt;this.db=db;this.json=json;}
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path=request.getServletPath().isEmpty()?request.getRequestURI():request.getServletPath();
        return ("POST".equals(request.getMethod()) && (path.equals("/api/auth/login") || path.equals("/api/auth/register")))
            || ("GET".equals(request.getMethod()) && path.equals("/api/blindboxes"));
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
        throws ServletException,IOException {
        String header=request.getHeader("Authorization");
        if(header!=null) {
            int id;
            try {
                if(header.length()>4096 || !header.startsWith("Bearer ")) throw new IllegalArgumentException();
                id=jwt.parse(header.substring(7));
            } catch(JwtException | IllegalArgumentException e) {error(response,401,"登录已过期，请重新登录");return;}
            User user;
            try {user=db.user(id);} catch(Exception e) {error(response,503,"认证服务暂不可用");return;}
            if(user==null) {error(response,401,"用户不存在");return;}
            Principal principal=new Principal(id,user.getAccount(),user.getRole());
            var context=SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken(
                principal,null,List.of(new SimpleGrantedAuthority("ROLE_"+user.getRole()))));
            SecurityContextHolder.setContext(context);
        }
        chain.doFilter(request,response);
    }
    private void error(HttpServletResponse response,int status,String message) throws IOException {
        response.setStatus(status);if(status==503) response.setHeader("Retry-After","1");response.setContentType("application/json;charset=UTF-8");
        json.writeValue(response.getWriter(),Result.error(status,message));
    }
}
