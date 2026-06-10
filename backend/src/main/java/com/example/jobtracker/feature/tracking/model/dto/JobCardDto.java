package com.example.jobtracker.feature.tracking.model.dto;

import java.time.LocalDateTime;

/**
 * API DTO for one job card.
 *
 * This is the individual saved opportunity inside a status lane. The Java field
 * names use job-specific language, while JSON keeps the shorter keys already
 * used by the frontend.
 */
public class JobCardDto {
    /** Unique database id of the job application. */
    private Long id;

    /** Employer/company name associated with the application. */
    private String companyName;

    /** Job title/role name. */
    private String jobTitle;

    /** Sort position of this card within its current status lane. */
    private int orderIndex;

    /** Id of the status lane that currently contains this job card. */
    private Long statusLaneId;

    /** Timestamp when this application record was created. */
    private LocalDateTime createdAt;

    /** Optional job location text captured from user input or scraping. */
    private String location;

    /** Optional free-form notes entered by the user. */
    private String notes;

    /** Optional salary/range text. */
    private String salary;

    /** Optional canonical link to the job posting. */
    private String jobUrl;

    public JobCardDto() {
    }

    public JobCardDto(Long id,
                           String companyName,
                           String jobTitle,
                           int orderIndex,
                           Long statusLaneId,
                           LocalDateTime createdAt) {
        this.id = id;
        this.companyName = companyName;
        this.jobTitle = jobTitle;
        this.orderIndex = orderIndex;
        this.statusLaneId = statusLaneId;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public int getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }

    public Long getStatusLaneId() {
        return statusLaneId;
    }

    public void setStatusLaneId(Long statusLaneId) {
        this.statusLaneId = statusLaneId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
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
