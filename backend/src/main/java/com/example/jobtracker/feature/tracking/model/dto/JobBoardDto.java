package com.example.jobtracker.feature.tracking.model.dto;

import java.util.List;

/**
 * API DTO for one job-search category board.
 *
 * A board is the top-level bucket a student chooses between, such as
 * "Internships" or "Full-Time Jobs". Status lanes and job cards live inside the
 * board.
 */
public class JobBoardDto {
    /** Unique database id of the board. */
    private Long id;

    /** Display name of the board/category, such as "Internships". */
    private String name;

    /** Ordered workflow lanes that belong to this board. */
    private List<JobStatusLaneDto> statusLanes;

    public JobBoardDto() {
    }

    public JobBoardDto(Long id, String name) {
        this.id = id;
        this.name = name;
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

    public List<JobStatusLaneDto> getStatusLanes() {
        return statusLanes;
    }

    public void setStatusLanes(List<JobStatusLaneDto> statusLanes) {
        this.statusLanes = statusLanes;
    }
}
