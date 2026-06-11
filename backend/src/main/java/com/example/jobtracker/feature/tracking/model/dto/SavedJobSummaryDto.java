package com.example.jobtracker.feature.tracking.model.dto;

import java.time.LocalDateTime;

/**
 * Flattened API DTO for saved-job list views.
 *
 * Each instance represents one table row. It includes the job identity fields
 * plus enough board/status information to show where the saved job currently
 * lives.
 */
public class SavedJobSummaryDto {
    /** Unique id of the saved job/application row. */
    private Long id;

    /** Job title shown in saved-job list results. */
    private String jobTitle;

    /** Company name shown in saved-job list results. */
    private String companyName;

    /** Optional job location text. */
    private String location;

    /** Optional salary/range text. */
    private String salary;

    /** Source URL for the job posting. */
    private String jobUrl;

    /** Optional user notes for this saved job. */
    private String notes;

    /** Name of the board containing this job. */
    private String boardName;

    /** Name of the status lane containing this job. */
    private String statusLaneName;

    /** Timestamp when this job was created/saved. */
    private LocalDateTime createdAt;

    public SavedJobSummaryDto() {
    }

    public SavedJobSummaryDto(Long id,
                                   String jobTitle,
                                   String companyName,
                                   String location,
                                   String salary,
                                   String jobUrl,
                                   String notes,
                                   String boardName,
                                   String statusLaneName,
                                   LocalDateTime createdAt) {
        this.id = id;
        this.jobTitle = jobTitle;
        this.companyName = companyName;
        this.location = location;
        this.salary = salary;
        this.jobUrl = jobUrl;
        this.notes = notes;
        this.boardName = boardName;
        this.statusLaneName = statusLaneName;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
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

    public String getJobUrl() {
        return jobUrl;
    }

    public void setJobUrl(String jobUrl) {
        this.jobUrl = jobUrl;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getBoardName() {
        return boardName;
    }

    public void setBoardName(String boardName) {
        this.boardName = boardName;
    }

    public String getStatusLaneName() {
        return statusLaneName;
    }

    public void setStatusLaneName(String statusLaneName) {
        this.statusLaneName = statusLaneName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
