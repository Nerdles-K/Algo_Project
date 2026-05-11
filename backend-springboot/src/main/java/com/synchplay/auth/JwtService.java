package com.synchplay.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirySeconds;

    public JwtService(@Value("${synchplay.jwt.secret}") String secret,
                      @Value("${synchplay.jwt.expiry-seconds}") long expirySeconds) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("synchplay.jwt.secret must be >= 32 bytes (HS256). Got " + bytes.length);
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expirySeconds = expirySeconds;
    }

    public String issueToken(AppUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(user.getUsername())
            .claims(Map.of(
                "uid",  user.getId(),
                "gnid", user.getGraphNodeId()))
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(expirySeconds)))
            .signWith(key)
            .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
