package com.example.jobtracker.feature.tracking.model.dto;

/**
 * API result DTO for "scrape then save" workflows.
 * Combines extracted listing data with the result of persistence into a board.
 */
public class ScrapeAndSaveResult {
    /** Scraped listing details extracted from URL/text input. */
    private ScrapedJobListingDto listing;

    /** Save result label (for example created, updated, duplicate-detected). */
    private String saveStatus;

    /** Board where the job was placed. */
    private String boardName;

    /** Status lane where the job was placed. */
    private String statusLaneName;

    /** Persisted job id when save operation succeeds. */
    private Long jobId;

    /** True when this company already has prior applications for the same user. */
    private boolean companyPreviouslyApplied;

    /** Count of prior applications for this company (excluding current save). */
    private int previousApplicationCount;

    public ScrapeAndSaveResult() {
    }

    public ScrapeAndSaveResult(ScrapedJobListingDto listing,
                                 String saveStatus,
                                 String boardName,
                                 String statusLaneName,
                                 Long jobId) {
        this(listing, saveStatus, boardName, statusLaneName, jobId, false, 0);
    }

    public ScrapeAndSaveResult(ScrapedJobListingDto listing,
                                 String saveStatus,
                                 String boardName,
                                 String statusLaneName,
                                 Long jobId,
                                 boolean companyPreviouslyApplied,
                                 int previousApplicationCount) {
        this.listing = listing;
        this.saveStatus = saveStatus;
        this.boardName = boardName;
        this.statusLaneName = statusLaneName;
        this.jobId = jobId;
        this.companyPreviouslyApplied = companyPreviouslyApplied;
        this.previousApplicationCount = previousApplicationCount;
    }

    public ScrapedJobListingDto getListing() {
        return listing;
    }

    public void setListing(ScrapedJobListingDto listing) {
        this.listing = listing;
    }

    public String getSaveStatus() {
        return saveStatus;
    }

    public void setSaveStatus(String saveStatus) {
        this.saveStatus = saveStatus;
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

    public Long getJobId() {
        return jobId;
    }

    public void setJobId(Long jobId) {
        this.jobId = jobId;
    }

    public boolean isCompanyPreviouslyApplied() {
        return companyPreviouslyApplied;
    }

    public void setCompanyPreviouslyApplied(boolean companyPreviouslyApplied) {
        this.companyPreviouslyApplied = companyPreviouslyApplied;
    }

    public int getPreviousApplicationCount() {
        return previousApplicationCount;
    }

    public void setPreviousApplicationCount(int previousApplicationCount) {
        this.previousApplicationCount = previousApplicationCount;
    }
}
