package com.example.demo.rag.security;

import com.example.demo.rag.RagException;
import com.example.demo.rag.config.AppSecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;

@Service
public class JwtTokenService {

    private final AppSecurityProperties securityProperties;
    private final SecretKey signingKey;

    public JwtTokenService(AppSecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
        this.signingKey = buildSigningKey(securityProperties.getJwtSecret());
    }

    public String createToken(AuthenticatedUser user) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(Math.max(300, securityProperties.getJwtExpireSeconds()));

        return Jwts.builder()
                .subject(user.getUsername())
                .claim("uid", user.getId())
                .claim("role", user.getRole())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    public boolean isTokenValid(String token, AuthenticatedUser user) {
        try {
            Claims claims = parseClaims(token);
            String username = claims.getSubject();
            return Objects.equals(username, user.getUsername()) && !isExpired(claims);
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public Long extractUserId(String token) {
        Object uid = parseClaims(token).get("uid");
        if (uid instanceof Integer intUid) {
            return intUid.longValue();
        }
        if (uid instanceof Long longUid) {
            return longUid;
        }
        if (uid instanceof String stringUid && !stringUid.isBlank()) {
            return Long.parseLong(stringUid);
        }
        throw new RagException("JWT uid claim is missing.");
    }

    public long getExpireSeconds() {
        return Math.max(300, securityProperties.getJwtExpireSeconds());
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private boolean isExpired(Claims claims) {
        Date expiration = claims.getExpiration();
        return expiration == null || expiration.before(new Date());
    }

    private SecretKey buildSigningKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new RagException("app.security.jwt-secret must not be empty.");
        }
        String normalizedSecret = secret.trim();
        byte[] raw;
        try {
            raw = Decoders.BASE64.decode(normalizedSecret);
            if (raw.length < 32) {
                raw = digest(normalizedSecret);
            }
        } catch (RuntimeException ex) {
            raw = normalizedSecret.getBytes(StandardCharsets.UTF_8);
            if (raw.length < 32) {
                raw = digest(normalizedSecret);
            }
        }
        return Keys.hmacShaKeyFor(raw);
    }

    private byte[] digest(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new RagException("Unable to initialize JWT secret key.", ex);
        }
    }
}
