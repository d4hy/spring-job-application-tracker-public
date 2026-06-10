package com.example.jobtracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe configuration holder for JWT security settings.
 * Binds external application properties (secret and token lifetime) into a single
 * object that authentication components can consume consistently at runtime.
 */
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {
    private String secret = "change-me-please-change-32-char-minimum";
    private int expirationMinutes = 60;

    
    
    public String getSecret() {
        return secret;
    }

    
    
    public void setSecret(String secret) {
        this.secret = secret;
    }

    
    
    public int getExpirationMinutes() {
        return expirationMinutes;
    }

    
    
    public void setExpirationMinutes(int expirationMinutes) {
        this.expirationMinutes = expirationMinutes;
    }
}
