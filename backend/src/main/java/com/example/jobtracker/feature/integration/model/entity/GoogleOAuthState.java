package com.example.jobtracker.feature.integration.model.entity;

import com.example.jobtracker.feature.auth.model.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "google_oauth_state",
        uniqueConstraints = @UniqueConstraint(name = "uk_google_oauth_state_token", columnNames = "state_token")
)
/**
 * JPA entity representing Google O Auth State in the persistence model.
 * Maps domain state and relationships to relational storage so repository and service
 * layers can work with strongly typed, lifecycle-managed objects.
 */
public class GoogleOAuthState {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private User user;

    @Column(name = "state_token", nullable = false, length = 255)
    private String stateToken;

    @Column(nullable = false)
    private boolean consumed;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    
    
    public Long getId() {
        return id;
    }

    
    
    public User getUser() {
        return user;
    }

    
    
    public void setUser(User user) {
        this.user = user;
    }

    
    
    public String getStateToken() {
        return stateToken;
    }

    
    
    public void setStateToken(String stateToken) {
        this.stateToken = stateToken;
    }

    
    
    public boolean isConsumed() {
        return consumed;
    }

    
    
    public void setConsumed(boolean consumed) {
        this.consumed = consumed;
    }

    
    
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    
    
    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    
    
    @PrePersist
    public void onCreate() {
        
        createdAt = LocalDateTime.now();
    }
}
