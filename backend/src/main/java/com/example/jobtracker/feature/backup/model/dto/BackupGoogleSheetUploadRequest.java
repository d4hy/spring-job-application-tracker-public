package com.example.jobtracker.feature.backup.model.dto;

/**
 * Request DTO for Backup Google Sheet Upload operations.
 * Represents client-provided input fields for API endpoints while keeping transport
 * contracts decoupled from internal entities and persistence models.
 */
public class BackupGoogleSheetUploadRequest {
    private String spreadsheet;
    private String sheetName;

    public BackupGoogleSheetUploadRequest() {
    }

    public BackupGoogleSheetUploadRequest(String spreadsheet, String sheetName) {
        this.spreadsheet = spreadsheet;
        this.sheetName = sheetName;
    }

    
    
    public String getSpreadsheet() {
        return spreadsheet;
    }

    
    
    public void setSpreadsheet(String spreadsheet) {
        this.spreadsheet = spreadsheet;
    }

    
    
    public String getSheetName() {
        return sheetName;
    }

    
    
    public void setSheetName(String sheetName) {
        this.sheetName = sheetName;
    }
}
