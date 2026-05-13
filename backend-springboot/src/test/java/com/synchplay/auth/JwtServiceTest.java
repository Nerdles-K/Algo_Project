package com.synchplay.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private static final String SECRET = "test_secret_at_least_32_bytes_long_for_HS256_xxxxxx";

    private AppUser newUser() {
        AppUser u = new AppUser("alice", "alice@test.com", "hashed", "user_42");
        // id is normally set by JPA on persist; for unit tests we inject via reflection.
        try {
            java.lang.reflect.Field f = AppUser.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(u, 1L);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return u;
    }

    @Test
    @DisplayName("Constructor rejects secret shorter than 32 bytes")
    void rejectsShortSecret() {
        assertThrows(IllegalStateException.class,
                () -> new JwtService("short", 3600));
    }

    @Test
    @DisplayName("Constructor accepts secret >= 32 bytes")
    void acceptsLongSecret() {
        assertDoesNotThrow(() -> new JwtService(SECRET, 3600));
    }

    @Test
    @DisplayName("issueToken produces a non-empty JWT")
    void issuesToken() {
        JwtService svc = new JwtService(SECRET, 3600);
        String token = svc.issueToken(newUser());
        assertNotNull(token);
        assertTrue(token.split("\\.").length == 3, "JWT must have header.payload.signature");
    }

    @Test
    @DisplayName("parse() round-trips username + custom claims")
    void parsesClaims() {
        JwtService svc = new JwtService(SECRET, 3600);
        String token = svc.issueToken(newUser());
        Claims c = svc.parse(token);

        assertEquals("alice", c.getSubject());
        assertEquals("user_42", c.get("gnid", String.class));
    }

    @Test
    @DisplayName("parse() rejects token signed with a different secret")
    void rejectsTamperedSignature() {
        JwtService issuer = new JwtService(SECRET, 3600);
        JwtService verifier = new JwtService("a_completely_different_secret_at_least_32_bytes_xxxxx", 3600);
        String token = issuer.issueToken(newUser());

        assertThrows(SignatureException.class, () -> verifier.parse(token));
    }

    @Test
    @DisplayName("parse() rejects expired token")
    void rejectsExpiredToken() throws Exception {
        // expirySeconds = -1 → token expires 1 second in the past
        JwtService svc = new JwtService(SECRET, -1);
        String token = svc.issueToken(newUser());

        assertThrows(ExpiredJwtException.class, () -> svc.parse(token));
    }

    @Test
    @DisplayName("parse() rejects malformed token")
    void rejectsMalformedToken() {
        JwtService svc = new JwtService(SECRET, 3600);
        assertThrows(Exception.class, () -> svc.parse("not.a.jwt"));
    }
}
