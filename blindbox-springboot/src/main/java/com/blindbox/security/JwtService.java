package com.blindbox.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtService {
    private final SecretKey key;private final long hours;
    public JwtService(@Value("${app.jwt-secret}") String secret,@Value("${app.jwt-hours:2}") long hours) {
        if(secret.startsWith("replace-") || secret.getBytes(StandardCharsets.UTF_8).length<32)
            throw new IllegalArgumentException("JWT_SECRET 必须替换为至少 32 字节的随机密钥");
        if(hours<1 || hours>24) throw new IllegalArgumentException("JWT_HOURS 须在 1 至 24 之间");
        this.key=Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));this.hours=hours;
    }
    public String generate(int id) {
        Instant now=Instant.now();
        return Jwts.builder().subject(Integer.toString(id)).issuer("blindbox").issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(hours*3600))).signWith(key).compact();
    }
    public int parse(String token) {
        return Integer.parseInt(Jwts.parser().verifyWith(key).requireIssuer("blindbox").build().parseSignedClaims(token).getPayload().getSubject());
    }
}
