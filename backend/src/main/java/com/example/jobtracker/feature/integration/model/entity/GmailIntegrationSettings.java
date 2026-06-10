package com.example.jobtracker.feature.integration.model.entity;

import com.example.jobtracker.feature.auth.model.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "gmail_integration_settings",
        uniqueConstraints = @UniqueConstraint(name = "uk_gmail_integration_user", columnNames = "user_id")
)
/**
 * JPA entity representing Gmail Integration in the persistence model.
 * Maps domain state and relationships to relational storage so repository and service
 * layers can work with strongly typed, lifecycle-managed objects.
 */
public class GmailIntegrationSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    private User user;

    @Column(nullable = false)
    private String gmailAddress;

    @Column(nullable = false, length = 500)
    private String appPassword;

    @Column(nullable = false)
    private boolean connected;

    @Column(nullable = false)
    private boolean autoSyncEnabled;

    @Column
    private LocalDateTime lastSyncedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    
    
    public Long getId() {
        return id;
    }

    
    
    public User getUser() {
        return user;
    }

    
    
    public void setUser(User user) {
        this.user = user;
    }

    
    
    public String getGmailAddress() {
        return gmailAddress;
    }

    
    
    public void setGmailAddress(String gmailAddress) {
        this.gmailAddress = gmailAddress;
    }

    
    
    public String getAppPassword() {
        return appPassword;
    }

    
    
    public void setAppPassword(String appPassword) {
        this.appPassword = appPassword;
    }

    
    
    public boolean isConnected() {
        return connected;
    }

    
    
    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    
    
    public boolean isAutoSyncEnabled() {
        return autoSyncEnabled;
    }

    
    
    public void setAutoSyncEnabled(boolean autoSyncEnabled) {
        this.autoSyncEnabled = autoSyncEnabled;
    }

    
    
    public LocalDateTime getLastSyncedAt() {
        return lastSyncedAt;
    }

    
    
    public void setLastSyncedAt(LocalDateTime lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }

    
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    
    
    @PrePersist
    public void onCreate() {
        
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    
    
    @PreUpdate
    public void onUpdate() {
        
        updatedAt = LocalDateTime.now();
    }
}
