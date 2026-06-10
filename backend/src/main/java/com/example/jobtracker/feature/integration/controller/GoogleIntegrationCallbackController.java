package com.example.jobtracker.feature.integration.controller;

import com.example.jobtracker.core.mode.OfflineModeException;
import com.example.jobtracker.core.mode.OfflineModeSupport;
import com.example.jobtracker.feature.integration.service.GoogleIntegrationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * REST controller for Google Integration Callback workflows.
 * Defines HTTP endpoints for this feature, resolves authenticated user context where
 * required, and delegates business orchestration to service-layer components.
 */
@RestController
@RequestMapping("/api/integrations/google")
public class GoogleIntegrationCallbackController {
    private final GoogleIntegrationService googleIntegrationService;
    private final String frontendBaseUrl;
    private final OfflineModeSupport offlineModeSupport;

    public GoogleIntegrationCallbackController(GoogleIntegrationService googleIntegrationService,
                                               OfflineModeSupport offlineModeSupport,
                                               @Value("${app.frontend.base-url:http://localhost:5173}") String frontendBaseUrl) {
        this.googleIntegrationService = googleIntegrationService;
        this.offlineModeSupport = offlineModeSupport;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam(value = "code", required = false) String code,
                                         @RequestParam(value = "state", required = false) String state) {
        if (offlineModeSupport.isEnabled()) {
            return redirectToFrontend("error", "Google integration is disabled because offline mode is enabled.");
        }

        try {
            
            googleIntegrationService.completeOAuthCallback(code, state);
            return redirectToFrontend("connected", "Google integration connected.");
        
        
        } catch (OfflineModeException e) {
            return redirectToFrontend("error", e.getMessage());
        
        
        } catch (IllegalArgumentException e) {
            return redirectToFrontend("error", e.getMessage());
        }
    }

    
    
    private ResponseEntity<Void> redirectToFrontend(String status, String message) {
        
        String encodedStatus = URLEncoder.encode(status, StandardCharsets.UTF_8);
        
        String encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8);
        
        String normalizedBaseUrl = normalizeBaseUrl(frontendBaseUrl);
        String location = normalizedBaseUrl + "/?view=integrations&integration=google"
                + "&integration_status=" + encodedStatus
                + "&integration_message=" + encodedMessage;
        return ResponseEntity.status(302)
                .header(HttpHeaders.LOCATION, location)
                
                .build();
    }

    
    
    private String normalizeBaseUrl(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "http://localhost:5173";
        }
        
        String trimmed = value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
