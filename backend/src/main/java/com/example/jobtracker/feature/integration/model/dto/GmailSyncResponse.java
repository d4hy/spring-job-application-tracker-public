package com.example.jobtracker.feature.integration.model.dto;

import java.time.LocalDateTime;

/**
 * Response DTO for Gmail Sync operations.
 * Defines the JSON payload returned to clients so API shape remains stable even when
 * internal domain models evolve.
 */
public class GmailSyncResponse {
    private int checkedEmails;
    private int updatedJobs;
    private int movedToApplied;
    private int movedToInterviewing;
    private int movedToOffer;
    private int movedToRejected;
    private LocalDateTime syncedAt;

    public GmailSyncResponse() {
    }

    public GmailSyncResponse(int checkedEmails,
                             int updatedJobs,
                             int movedToApplied,
                             int movedToInterviewing,
                             int movedToOffer,
                             int movedToRejected,
                             LocalDateTime syncedAt) {
        this.checkedEmails = checkedEmails;
        this.updatedJobs = updatedJobs;
        this.movedToApplied = movedToApplied;
        this.movedToInterviewing = movedToInterviewing;
        this.movedToOffer = movedToOffer;
        this.movedToRejected = movedToRejected;
        this.syncedAt = syncedAt;
    }

    
    
    public int getCheckedEmails() {
        return checkedEmails;
    }

    
    
    public void setCheckedEmails(int checkedEmails) {
        this.checkedEmails = checkedEmails;
    }

    
    
    public int getUpdatedJobs() {
        return updatedJobs;
    }

    
    
    public void setUpdatedJobs(int updatedJobs) {
        this.updatedJobs = updatedJobs;
    }

    
    
    public int getMovedToApplied() {
        return movedToApplied;
    }

    
    
    public void setMovedToApplied(int movedToApplied) {
        this.movedToApplied = movedToApplied;
    }

    
    
    public int getMovedToInterviewing() {
        return movedToInterviewing;
    }

    
    
    public void setMovedToInterviewing(int movedToInterviewing) {
        this.movedToInterviewing = movedToInterviewing;
    }

    
    
    public int getMovedToOffer() {
        return movedToOffer;
    }

    
    
    public void setMovedToOffer(int movedToOffer) {
        this.movedToOffer = movedToOffer;
    }

    
    
    public int getMovedToRejected() {
        return movedToRejected;
    }

    
    
    public void setMovedToRejected(int movedToRejected) {
        this.movedToRejected = movedToRejected;
    }

    
    
    public LocalDateTime getSyncedAt() {
        return syncedAt;
    }

    
    
    public void setSyncedAt(LocalDateTime syncedAt) {
        this.syncedAt = syncedAt;
    }
}
