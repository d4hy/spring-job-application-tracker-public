package com.example.jobtracker.feature.tracking.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * API request model for extracting job data from raw text content.
 * Supports optional source URL context and optional applied-state behavior.
 */
public class ScrapeTextRequest {
    /** Raw posting text or page text to parse for job metadata. */
    @NotBlank(message = "text is required")
    @Size(max = 120000, message = "text is too long")
    private String text;

    /** Optional source URL associated with the text content. */
    @Size(max = 2000, message = "url is too long")
    private String url;

    /** When true, the saved job is placed into an "applied" flow/state. */
    private boolean markApplied;

    /** Target board bucket for saved jobs, such as "Internships" or "Full-Time Jobs". */
    @Size(max = 100, message = "board name is too long")
    private String boardName;

    public ScrapeTextRequest() {
    }

    public ScrapeTextRequest(String text, String url) {
        this.text = text;
        this.url = url;
    }

    
    
    public String getText() {
        return text;
    }

    
    
    public void setText(String text) {
        this.text = text;
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
