package com.synchplay.config;

import com.synchplay.auth.AppUser;
import com.synchplay.auth.AppUserRepository;
import com.synchplay.auth.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link JwtAuthenticationFilter} — the security boundary that
 * turns a {@code Bearer} token into an authenticated {@link AppUser} principal
 * with a role authority (FR-A.3, FR-A.8).
 *
 * Uses a real {@link JwtService} + real signed tokens (no Claims mocking, which
 * is fragile because jjwt's {@code getSubject()} is a default method). Only the
 * user repository is mocked, so no Spring context / database is needed.
 *
 * In the same package as the filter so we can invoke the protected
 * {@code doFilterInternal} via {@code doFilter}.
 */
class JwtAuthenticationFilterTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-1234"; // >= 32 bytes

    private final SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    private AppUserRepository userRepo;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        userRepo = mock(AppUserRepository.class);
        JwtService jwtService = new JwtService(SECRET, 3600);
        filter = new JwtAuthenticationFilter(jwtService, userRepo);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /** Build a real signed token with the given subject + optional role claim. */
    private String token(String subject, String role) {
        var builder = Jwts.builder()
            .subject(subject)
            .issuedAt(Date.from(Instant.now()))
            .expiration(Date.from(Instant.now().plusSeconds(3600)));
        if (role != null) builder.claim("role", role);
        return builder.signWith(key).compact();
    }

    private MockHttpServletResponse runFilter(String authHeader) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        if (authHeader != null) req.addHeader("Authorization", authHeader);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        return res;
    }

    @Test
    @DisplayName("valid token populates SecurityContext with AppUser principal + ROLE_ authority")
    void validTokenAuthenticates() throws Exception {
        AppUser admin = new AppUser("demo1", "demo1@example.com", "hash", "user_1", "ADMIN");
        when(userRepo.findByUsername("demo1")).thenReturn(Optional.of(admin));

        runFilter("Bearer " + token("demo1", "ADMIN"));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth, "context should be authenticated");
        assertSame(admin, auth.getPrincipal(), "principal must be the AppUser");
        assertTrue(auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")),
            "role claim must become ROLE_ADMIN authority");
    }

    @Test
    @DisplayName("missing role claim defaults to ROLE_USER")
    void missingRoleDefaultsToUser() throws Exception {
        AppUser plain = new AppUser("bob", "bob@example.com", "hash", "user_2"); // default USER
        when(userRepo.findByUsername("bob")).thenReturn(Optional.of(plain));

        runFilter("Bearer " + token("bob", null));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertTrue(auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
    }

    @Test
    @DisplayName("no Authorization header leaves context unauthenticated")
    void noHeaderSkips() throws Exception {
        runFilter(null);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(userRepo);
    }

    @Test
    @DisplayName("malformed/invalid token is swallowed and leaves context unauthenticated")
    void invalidTokenSwallowed() throws Exception {
        runFilter("Bearer not-a-real-jwt");

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(userRepo, never()).findByUsername(anyString());
    }

    @Test
    @DisplayName("token signed with a different key is rejected")
    void wrongSignatureRejected() throws Exception {
        SecretKey otherKey = Keys.hmacShaKeyFor(
            "a-totally-different-secret-key-32bytes!!".getBytes(StandardCharsets.UTF_8));
        String forged = Jwts.builder().subject("demo1").claim("role", "ADMIN")
            .expiration(Date.from(Instant.now().plusSeconds(3600)))
            .signWith(otherKey).compact();

        runFilter("Bearer " + forged);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(userRepo, never()).findByUsername(anyString());
    }

    @Test
    @DisplayName("unknown user in token leaves context unauthenticated")
    void unknownUserNotAuthenticated() throws Exception {
        when(userRepo.findByUsername("ghost")).thenReturn(Optional.empty());

        runFilter("Bearer " + token("ghost", "USER"));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
