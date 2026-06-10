package com.example.jobtracker.feature.tracking.model.dto;

/**
 * API request model for manually creating one job card.
 *
 * This DTO represents the user's "add a job" form. Field names match the job
 * tracker domain instead of generic UI/table wording.
 */
public class CreateJobRequest {
    /** Company name for the new application. */
    private String companyName;

    /** Job title for the new application. */
    private String jobTitle;

    /** Target category board id where this job should be created. */
    private Long boardId;

    /** Target status lane id where this job should be placed initially. */
    private Long statusLaneId;

    /** Optional location text. */
    private String location;

    /** Optional user notes. */
    private String notes;

    /** Optional salary/range text. */
    private String salary;

    /** Optional source posting URL. */
    private String jobUrl;

    public CreateJobRequest() {
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
    }

    public Long getBoardId() {
        return boardId;
    }

    public void setBoardId(Long boardId) {
        this.boardId = boardId;
    }

    public Long getStatusLaneId() {
        return statusLaneId;
    }

    public void setStatusLaneId(Long statusLaneId) {
        this.statusLaneId = statusLaneId;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
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
}
