package com.example.jobtracker.feature.backup.model.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO model for Backup Board data exchanged through the API.
 * Provides a transport-focused representation that isolates endpoint contracts from
 * domain internals and database-mapped entities.
 */
public class BackupBoardData {
    private String name;
    private List<BackupColumnData> columns = new ArrayList<>();

    
    
    public String getName() {
        return name;
    }

    
    
    public void setName(String name) {
        this.name = name;
    }

    
    
    public List<BackupColumnData> getColumns() {
        return columns;
    }

    
    
    public void setColumns(List<BackupColumnData> columns) {
        this.columns = columns;
    }
}
