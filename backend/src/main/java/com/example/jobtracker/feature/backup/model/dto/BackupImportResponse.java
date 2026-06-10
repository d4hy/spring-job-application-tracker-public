package com.example.jobtracker.feature.backup.model.dto;

/**
 * Response DTO for Backup Import operations.
 * Defines the JSON payload returned to clients so API shape remains stable even when
 * internal domain models evolve.
 */
public class BackupImportResponse {
    private String message;
    private int boardsImported;
    private int columnsImported;
    private int jobsImported;

    public BackupImportResponse() {
    }

    public BackupImportResponse(String message, int boardsImported, int columnsImported, int jobsImported) {
        this.message = message;
        this.boardsImported = boardsImported;
        this.columnsImported = columnsImported;
        this.jobsImported = jobsImported;
    }

    
    
    public String getMessage() {
        return message;
    }

    
    
    public void setMessage(String message) {
        this.message = message;
    }

    
    
    public int getBoardsImported() {
        return boardsImported;
    }

    
    
    public void setBoardsImported(int boardsImported) {
        this.boardsImported = boardsImported;
    }

    
    
    public int getColumnsImported() {
        return columnsImported;
    }

    
    
    public void setColumnsImported(int columnsImported) {
        this.columnsImported = columnsImported;
    }

    
    
    public int getJobsImported() {
        return jobsImported;
    }

    
    
    public void setJobsImported(int jobsImported) {
        this.jobsImported = jobsImported;
    }
}
