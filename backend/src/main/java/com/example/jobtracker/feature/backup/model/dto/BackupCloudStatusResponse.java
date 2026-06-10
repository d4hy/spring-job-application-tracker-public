package com.example.jobtracker.feature.backup.model.dto;

/**
 * Response DTO for Backup Cloud Status operations.
 * Defines the JSON payload returned to clients so API shape remains stable even when
 * internal domain models evolve.
 */
public class BackupCloudStatusResponse {
    private boolean configured;
    private String destination;

    public BackupCloudStatusResponse() {
    }

    public BackupCloudStatusResponse(boolean configured, String destination) {
        this.configured = configured;
        this.destination = destination;
    }

    
    
    public boolean isConfigured() {
        return configured;
    }

    
    
    public void setConfigured(boolean configured) {
        this.configured = configured;
    }

    
    
    public String getDestination() {
        return destination;
    }

    
    
    public void setDestination(String destination) {
        this.destination = destination;
    }
}
