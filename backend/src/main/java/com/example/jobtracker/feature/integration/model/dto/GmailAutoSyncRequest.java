package com.example.jobtracker.feature.integration.model.dto;

/**
 * Request DTO for Gmail Auto Sync operations.
 * Represents client-provided input fields for API endpoints while keeping transport
 * contracts decoupled from internal entities and persistence models.
 */
public class GmailAutoSyncRequest {
    private boolean enabled;

    
    
    public boolean isEnabled() {
        return enabled;
    }

    
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
