package com.example.jobtracker.feature.tracking.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * API request model for scraping a single job URL.
 * Used when the client submits a direct posting link.
 */
public class ScrapeLinkRequest {
    /** Job posting URL to scrape. */
    @NotBlank(message = "url is required")
    @Size(max = 2000, message = "url is too long")
    private String url;

    /** When true, the saved job is placed into an "applied" flow/state. */
    private boolean markApplied;

    /** Target board bucket for saved jobs, such as "Internships" or "Full-Time Jobs". */
    @Size(max = 100, message = "board name is too long")
    private String boardName;

    public ScrapeLinkRequest() {
    }

    public ScrapeLinkRequest(String url) {
        this.url = url;
    }

    
    
    public String getUrl() {
        return url;
    }

    
    
    public void setUrl(String url) {
        this.url = url;
    }

    
    
    public boolean isMarkApplied() {
        return markApplied;
    }

    
    
    public void setMarkApplied(boolean markApplied) {
        this.markApplied = markApplied;
    }

    
    
    public String getBoardName() {
        return boardName;
    }

    
    
    public void setBoardName(String boardName) {
        this.boardName = boardName;
    }
}
