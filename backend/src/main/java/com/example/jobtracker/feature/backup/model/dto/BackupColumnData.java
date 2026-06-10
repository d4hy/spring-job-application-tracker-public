package com.example.jobtracker.feature.backup.model.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO model for Backup Column data exchanged through the API.
 * Provides a transport-focused representation that isolates endpoint contracts from
 * domain internals and database-mapped entities.
 */
public class BackupColumnData {
    private String name;
    private Integer orderIndex;
    private List<BackupJobData> jobs = new ArrayList<>();

    
    
    public String getName() {
        return name;
    }

    
    
    public void setName(String name) {
        this.name = name;
    }

    
    
    public Integer getOrderIndex() {
        return orderIndex;
    }

    
    
    public void setOrderIndex(Integer orderIndex) {
        this.orderIndex = orderIndex;
    }

    
    
    public List<BackupJobData> getJobs() {
        return jobs;
    }

    
    
    public void setJobs(List<BackupJobData> jobs) {
        this.jobs = jobs;
    }
}
