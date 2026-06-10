package com.example.jobtracker.feature.backup.model.dto;

/**
 * DTO model for Backup Job data exchanged through the API.
 * Provides a transport-focused representation that isolates endpoint contracts from
 * domain internals and database-mapped entities.
 */
public class BackupJobData {
    private String company;
    private String title;
    private Integer orderIndex;
    private String location;
    private String salary;
    private String notes;
    private String jobUrl;
    private String createdAt;

    
    
    public String getCompany() {
        return company;
    }

    
    
    public void setCompany(String company) {
        this.company = company;
    }

    
    
    public String getTitle() {
        return title;
    }

    
    
    public void setTitle(String title) {
        this.title = title;
    }

    
    
    public Integer getOrderIndex() {
        return orderIndex;
    }

    
    
    public void setOrderIndex(Integer orderIndex) {
        this.orderIndex = orderIndex;
    }

    
    
    public String getLocation() {
        return location;
    }

    
    
    public void setLocation(String location) {
        this.location = location;
    }

    
    
    public String getSalary() {
        return salary;
    }

    
    
    public void setSalary(String salary) {
        this.salary = salary;
    }

    
    
    public String getNotes() {
        return notes;
    }

    
    
    public void setNotes(String notes) {
        this.notes = notes;
    }

    
    
    public String getJobUrl() {
        return jobUrl;
    }

    
    
    public void setJobUrl(String jobUrl) {
        this.jobUrl = jobUrl;
    }

    
    
    public String getCreatedAt() {
        return createdAt;
    }

    
    
    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
