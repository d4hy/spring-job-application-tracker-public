package com.example.jobtracker.feature.tracking.model.dto;

/**
 * API DTO for normalized scraper output.
 * Represents one extracted job listing before or after saving.
 */
public class ScrapedJobListingDto {
    /** Extracted job title. */
    private String title;

    /** Extracted company name. */
    private String company;

    /** Extracted location text. */
    private String location;

    /** Extracted salary/range text when available. */
    private String salary;

    /** Original posting URL tied to this listing. */
    private String originalLink;

    /** Source provider or parser pathway used to extract the listing. */
    private String source;

    public ScrapedJobListingDto() {
    }

    public ScrapedJobListingDto(String title, String company, String location, String salary, String originalLink, String source) {
        this.title = title;
        this.company = company;
        this.location = location;
        this.salary = salary;
        this.originalLink = originalLink;
        this.source = source;
    }

    
    
    public String getTitle() {
        return title;
    }

    
    
    public void setTitle(String title) {
        this.title = title;
    }

    
    
    public String getCompany() {
        return company;
    }

    
    
    public void setCompany(String company) {
        this.company = company;
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

    
    
    public String getOriginalLink() {
        return originalLink;
    }

    
    
    public void setOriginalLink(String originalLink) {
        this.originalLink = originalLink;
    }

    
    
    public String getSource() {
        return source;
    }

    
    
    public void setSource(String source) {
        this.source = source;
    }
}
