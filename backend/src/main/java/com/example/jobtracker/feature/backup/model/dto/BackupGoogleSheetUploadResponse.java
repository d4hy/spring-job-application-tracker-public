package com.example.jobtracker.feature.backup.model.dto;

/**
 * Response DTO for Backup Google Sheet Upload operations.
 * Defines the JSON payload returned to clients so API shape remains stable even when
 * internal domain models evolve.
 */
public class BackupGoogleSheetUploadResponse {
    private boolean uploaded;
    private String spreadsheetId;
    private String sheetName;
    private int rowsWritten;
    private String message;

    public BackupGoogleSheetUploadResponse() {
    }

    public BackupGoogleSheetUploadResponse(boolean uploaded,
                                           String spreadsheetId,
                                           String sheetName,
                                           int rowsWritten,
                                           String message) {
        this.uploaded = uploaded;
        this.spreadsheetId = spreadsheetId;
        this.sheetName = sheetName;
        this.rowsWritten = rowsWritten;
        this.message = message;
    }

    
    
    public boolean isUploaded() {
        return uploaded;
    }

    
    
    public void setUploaded(boolean uploaded) {
        this.uploaded = uploaded;
    }

    
    
    public String getSpreadsheetId() {
        return spreadsheetId;
    }

    
    
    public void setSpreadsheetId(String spreadsheetId) {
        this.spreadsheetId = spreadsheetId;
    }

    
    
    public String getSheetName() {
        return sheetName;
    }

    
    
    public void setSheetName(String sheetName) {
        this.sheetName = sheetName;
    }

    
    
    public int getRowsWritten() {
        return rowsWritten;
    }

    
    
    public void setRowsWritten(int rowsWritten) {
        this.rowsWritten = rowsWritten;
    }

    
    
    public String getMessage() {
        return message;
    }

    
    
    public void setMessage(String message) {
        this.message = message;
    }
}
