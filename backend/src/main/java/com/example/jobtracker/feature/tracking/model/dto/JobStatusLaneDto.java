package com.example.jobtracker.feature.tracking.model.dto;

import java.util.List;

/**
 * API DTO for one status lane inside a job board.
 *
 * In the UI, this is one workflow step such as "Wish List", "Applied",
 * "Interviewing", "Offer", or "Rejected". It is not another board. It is the
 * status lane that holds job cards for a board like "Internships".
 */
public class JobStatusLaneDto {
    /** Unique database id of this status lane. */
    private Long id;

    /** Human-readable lane name, such as "Applied" or "Interviewing". */
    private String name;

    /** Sort position among sibling status lanes in the same board. */
    private int orderIndex;

    /** Ordered job cards currently assigned to this status lane. */
    private List<JobCardDto> jobs;

    public JobStatusLaneDto() {
    }

    public JobStatusLaneDto(Long id, String name, int orderIndex) {
        this.id = id;
        this.name = name;
        this.orderIndex = orderIndex;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }

    public List<JobCardDto> getJobs() {
        return jobs;
    }

    public void setJobs(List<JobCardDto> jobs) {
        this.jobs = jobs;
    }
}
