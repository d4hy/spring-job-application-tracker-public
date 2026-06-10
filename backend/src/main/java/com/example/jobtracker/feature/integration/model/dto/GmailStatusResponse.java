package com.example.jobtracker.feature.integration.model.dto;

import java.time.LocalDateTime;

/**
 * Response DTO for Gmail Status operations.
 * Defines the JSON payload returned to clients so API shape remains stable even when
 * internal domain models evolve.
 */
public class GmailStatusResponse {
    private boolean connected;
    private boolean autoSyncEnabled;
    private String gmailAddress;
    private LocalDateTime lastSyncedAt;

    public GmailStatusResponse() {
    }

    public GmailStatusResponse(boolean connected,
                               boolean autoSyncEnabled,
                               String gmailAddress,
                               LocalDateTime lastSyncedAt) {
        this.connected = connected;
        this.autoSyncEnabled = autoSyncEnabled;
        this.gmailAddress = gmailAddress;
        this.lastSyncedAt = lastSyncedAt;
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

    
    
    public String getGmailAddress() {
        return gmailAddress;
    }

    
    
    public void setGmailAddress(String gmailAddress) {
        this.gmailAddress = gmailAddress;
    }

    
    
    public LocalDateTime getLastSyncedAt() {
        return lastSyncedAt;
    }

    
    
    public void setLastSyncedAt(LocalDateTime lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }
}
