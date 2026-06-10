package com.example.jobtracker.feature.tracking.model.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * JPA entity representing Job in the persistence model.
 * Maps domain state and relationships to relational storage so repository and service
 * layers can work with strongly typed, lifecycle-managed objects.
 */
@Entity
@Table(name = "job_applications")
public class JobApplication {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String company;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private int orderIndex;

    @Column
    private String location;

    @Column
    private String salary;

    @Column(length = 2000)
    private String notes;

    @Column(length = 2000)
    private String jobUrl;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @ManyToOne(optional = false)
    private BoardColumn column;

    public JobApplication() {
    }

    public JobApplication(String company, String title, int orderIndex, BoardColumn column) {
        this.company = company;
        this.title = title;
        this.orderIndex = orderIndex;
        this.column = column;
    }

    
    
    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            
            createdAt = LocalDateTime.now();
        }
    }

    
    
    public Long getId() {
        return id;
    }

    
    
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

    
    
    public int getOrderIndex() {
        return orderIndex;
    }

    
    
    public void setOrderIndex(int orderIndex) {
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

    
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    
    
    public BoardColumn getColumn() {
        return column;
    }

    
    
    public void setColumn(BoardColumn column) {
        this.column = column;
    }
}
