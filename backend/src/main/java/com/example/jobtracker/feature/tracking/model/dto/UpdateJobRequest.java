package com.example.jobtracker.feature.tracking.model.dto;

/**
 * API request model for editing visible details on an existing job card.
 * Moving between status lanes is handled separately by query params or status update requests.
 */
public class UpdateJobRequest {
    /** Updated company name. */
    private String companyName;

    /** Updated job title. */
    private String jobTitle;

    /** Updated location text. */
    private String location;

    /** Updated notes text. */
    private String notes;

    /** Updated salary/range text. */
    private String salary;

    /** Updated job posting URL. */
    private String jobUrl;

    public UpdateJobRequest() {
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
