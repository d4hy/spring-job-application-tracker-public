package com.example.jobtracker.feature.integration.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for launching local job-form autofill.
 * The URL can point to any http/https job application page. Resume fields are optional.
 */
public class WorkdayAutofillRequest {
    /** Job application form URL to open in the local browser. */
    @NotBlank(message = "url is required")
    private String url;

    /** Original uploaded resume filename, used for logging/context. */
    private String resumeFileName;

    /** Browser-provided MIME type for the uploaded resume. */
    private String resumeMimeType;

    /** Base64 resume bytes passed from frontend to backend for local Playwright upload. */
    @Size(max = 12_000_000, message = "resume content is too large")
    private String resumeContentBase64;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getResumeFileName() {
        return resumeFileName;
    }

    public void setResumeFileName(String resumeFileName) {
        this.resumeFileName = resumeFileName;
    }

    public String getResumeMimeType() {
        return resumeMimeType;
    }

    public void setResumeMimeType(String resumeMimeType) {
        this.resumeMimeType = resumeMimeType;
    }

    public String getResumeContentBase64() {
        return resumeContentBase64;
    }

    public void setResumeContentBase64(String resumeContentBase64) {
        this.resumeContentBase64 = resumeContentBase64;
    }
}
