package com.example.jobtracker.feature.tracking.model.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * API request model for status-based job transitions.
 * Used by endpoints that move a job card to the status lane mapped from status.
 */
public class UpdateJobStatusRequest {
    /**
     * Requested status value.
     * Typical values in this project include: applied, accepted, rejected.
     */
    @NotBlank(message = "status is required")
    private String status;

    
    
    public String getStatus() {
        return status;
    }

    
    
    public void setStatus(String status) {
        this.status = status;
    }
}
