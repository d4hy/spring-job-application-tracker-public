package com.example.jobtracker.feature.backup.model.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Transport payload model for Tracker Backup data.
 * Groups related export/import fields into a serializable structure used to move
 * complete feature snapshots across API boundaries.
 */
public class TrackerBackupPayload {
    private String formatVersion;
    private String exportedAt;
    private String username;
    private List<BackupBoardData> boards = new ArrayList<>();

    
    
    public String getFormatVersion() {
        return formatVersion;
    }

    
    
    public void setFormatVersion(String formatVersion) {
        this.formatVersion = formatVersion;
    }

    
    
    public String getExportedAt() {
        return exportedAt;
    }

    
    
    public void setExportedAt(String exportedAt) {
        this.exportedAt = exportedAt;
    }

    
    
    public String getUsername() {
        return username;
    }

    
    
    public void setUsername(String username) {
        this.username = username;
    }

    
    
    public List<BackupBoardData> getBoards() {
        return boards;
    }

    
    
    public void setBoards(List<BackupBoardData> boards) {
        this.boards = boards;
    }
}
