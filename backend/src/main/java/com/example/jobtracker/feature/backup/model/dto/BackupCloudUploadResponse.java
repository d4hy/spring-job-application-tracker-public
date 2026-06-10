package com.example.jobtracker.feature.backup.model.dto;

/**
 * Response DTO for Backup Cloud Upload operations.
 * Defines the JSON payload returned to clients so API shape remains stable even when
 * internal domain models evolve.
 */
public class BackupCloudUploadResponse {
    private boolean uploaded;
    private int statusCode;
    private String destination;
    private String message;

    public BackupCloudUploadResponse() {
    }

    public BackupCloudUploadResponse(boolean uploaded, int statusCode, String destination, String message) {
        this.uploaded = uploaded;
        this.statusCode = statusCode;
        this.destination = destination;
        this.message = message;
    }

    
    
    public boolean isUploaded() {
        return uploaded;
    }

    
    
    public void setUploaded(boolean uploaded) {
        this.uploaded = uploaded;
    }

    
    
    public int getStatusCode() {
        return statusCode;
    }

    
    
    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    
    
    public String getDestination() {
        return destination;
    }

    
    
    public void setDestination(String destination) {
        this.destination = destination;
    }

    
    
    public String getMessage() {
        return message;
    }

    
    
    public void setMessage(String message) {
        this.message = message;
    }
}
