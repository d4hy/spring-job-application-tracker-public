package com.example.jobtracker.feature.integration.model.dto;

/**
 * Response DTO returned after requesting local job-form autofill.
 * "started" only means the local browser automation was launched; the user must still
 * review and submit the application manually.
 */
public class WorkdayAutofillResponse {
    /** True when the backend successfully started the local Playwright process. */
    private boolean started;

    /** Human-readable launch status or failure context. */
    private String message;

    public WorkdayAutofillResponse() {
    }

    public WorkdayAutofillResponse(boolean started, String message) {
        this.started = started;
        this.message = message;
    }

    public boolean isStarted() {
        return started;
    }

    public void setStarted(boolean started) {
        this.started = started;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
