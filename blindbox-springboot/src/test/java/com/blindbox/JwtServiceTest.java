package com.blindbox;
import com.blindbox.security.JwtService;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class JwtServiceTest {
    private static final String FIXTURE="offline-test-secret-0123456789-abcdef";
    @Test void signedTokenRoundTripsAndDifferentSecretRejects() {
        JwtService jwt=new JwtService(FIXTURE,2);String token=jwt.generate(7);
        assertEquals(7,jwt.parse(token));
        assertThrows(JwtException.class,()->new JwtService(FIXTURE+"-other",2).parse(token));
    }
    @Test void unsafeConfigurationAndMalformedTokenReject() {
        assertThrows(IllegalArgumentException.class,()->new JwtService("short",2));
        assertThrows(IllegalArgumentException.class,()->new JwtService("replace-with-a-long-enough-placeholder-secret",2));
        assertThrows(IllegalArgumentException.class,()->new JwtService(FIXTURE,0));
        assertThrows(JwtException.class,()->new JwtService(FIXTURE,2).parse("invalid"));
    }
}
