package com.example.jobtracker.feature.integration.model.dto;

/**
 * Response DTO for Integration Authorize operations.
 * Defines the JSON payload returned to clients so API shape remains stable even when
 * internal domain models evolve.
 */
public class IntegrationAuthorizeResponse {
    private String authorizationUrl;

    public IntegrationAuthorizeResponse() {
    }

    public IntegrationAuthorizeResponse(String authorizationUrl) {
        this.authorizationUrl = authorizationUrl;
    }

    
    
    public String getAuthorizationUrl() {
        return authorizationUrl;
    }

    
    
    public void setAuthorizationUrl(String authorizationUrl) {
        this.authorizationUrl = authorizationUrl;
    }
}
