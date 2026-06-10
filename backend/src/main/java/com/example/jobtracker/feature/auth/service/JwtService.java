package com.example.jobtracker.feature.auth.service;

import com.example.jobtracker.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Service-layer component for Jwt workflows.
 * Centralizes business rules, validation, and orchestration across repositories and
 * external integrations so controllers remain focused on request/response handling.
 */
@Service
public class JwtService {
    private static final int MIN_SECRET_LENGTH = 32;
    private static final int DEFAULT_EXPIRATION_MINUTES = 60;

    private final JwtProperties jwtProperties;
    private final SecretKey key;
    private final int expirationMinutes;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        
        String configuredSecret = jwtProperties.getSecret();
        if (configuredSecret == null || configuredSecret.isBlank()) {
            configuredSecret = "change-me-please-change-32-char-minimum";
        }
        if (configuredSecret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException("JWT secret must be at least 32 characters. Set app.jwt.secret or JWT_SECRET.");
        }

        
        int configuredExpirationMinutes = jwtProperties.getExpirationMinutes();
        this.expirationMinutes = configuredExpirationMinutes > 0 ? configuredExpirationMinutes : DEFAULT_EXPIRATION_MINUTES;

        // Generate SecretKey from string - IMPORTANT: Keep secret safe!
        this.key = Keys.hmacShaKeyFor(configuredSecret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate JWT token for a user
     * LEARNING NOTE: Claims are the payload of the JWT - they contain user info and metadata
     */
    public String generateToken(String username, Long userId) {
        Map<String, Object> claims = new HashMap<>();
        
        claims.put("userId", userId);
        
        claims.put("username", username);

        long expirationTimeInMillis = expirationMinutes * 60 * 1000L;
        
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationTimeInMillis);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(key, SignatureAlgorithm.HS256)
                
                .compact();
    }

    /**
     * Extract username from token
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extract userId from token claims
     * LEARNING NOTE: Custom claims can be added and retrieved from tokens
     */
    public Long extractUserId(String token) {
        
        Claims claims = extractAllClaims(token);
        return ((Number) claims.get("userId")).longValue();
    }

    /**
     * Validate token signature and expiration
     * LEARNING NOTE: Validation throws exceptions if token is invalid
     */
    public boolean isTokenValid(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    
                    .parseClaimsJws(token);
            return true;
        
        
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Extract specific claim from token
     */
    private <T> T extractClaim(String token, java.util.function.Function<Claims, T> claimsResolver) {
        
        Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Extract all claims from token
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                
                .getBody();
    }
}

