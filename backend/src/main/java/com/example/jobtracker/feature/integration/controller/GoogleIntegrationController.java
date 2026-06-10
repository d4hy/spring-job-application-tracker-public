package com.example.jobtracker.feature.integration.controller;

import com.example.jobtracker.feature.auth.model.entity.User;
import com.example.jobtracker.feature.integration.service.GmailSyncService;
import com.example.jobtracker.feature.integration.service.GoogleIntegrationService;
import com.example.jobtracker.feature.auth.service.UserService;
import com.example.jobtracker.feature.integration.model.dto.GmailAutoSyncRequest;
import com.example.jobtracker.feature.integration.model.dto.GmailStatusResponse;
import com.example.jobtracker.feature.integration.model.dto.GmailSyncResponse;
import com.example.jobtracker.feature.integration.model.dto.IntegrationAuthorizeResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for Google Integration workflows.
 * Defines HTTP endpoints for this feature, resolves authenticated user context where
 * required, and delegates business orchestration to service-layer components.
 */
@RestController
@RequestMapping("/api/integrations/google")
public class GoogleIntegrationController {
    private final GmailSyncService gmailSyncService;
    private final GoogleIntegrationService googleIntegrationService;
    private final UserService userService;

    public GoogleIntegrationController(GmailSyncService gmailSyncService,
                                       GoogleIntegrationService googleIntegrationService,
                                       UserService userService) {
        this.gmailSyncService = gmailSyncService;
        this.googleIntegrationService = googleIntegrationService;
        this.userService = userService;
    }

    
    
    @GetMapping("/status")
    public ResponseEntity<GmailStatusResponse> getStatus() {
        
        User user = getCurrentUser();
        return ResponseEntity.ok(gmailSyncService.getStatus(user));
    }

    
    
    @GetMapping("/authorize")
    public ResponseEntity<?> authorize() {
        try {
            
            User user = getCurrentUser();
            
            String authorizationUrl = googleIntegrationService.createAuthorizationUrl(user);
            return ResponseEntity.ok(new IntegrationAuthorizeResponse(authorizationUrl));
        
        
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    
    
    @PostMapping("/auto-sync")
    public ResponseEntity<?> setAutoSync(@RequestBody GmailAutoSyncRequest request) {
        try {
            
            User user = getCurrentUser();
            GmailStatusResponse status = gmailSyncService.setAutoSync(user, request.isEnabled());
            return ResponseEntity.ok(status);
        
        
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    
    
    @PostMapping("/sync")
    public ResponseEntity<?> syncNow() {
        try {
            
            User user = getCurrentUser();
            
            GmailSyncResponse response = gmailSyncService.syncNow(user);
            return ResponseEntity.ok(response);
        
        
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    
    
    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        String username = authentication.getName();
        return userService.findByUsername(username);
    }

    /**
     * Simple API error payload used by the Google Integration endpoints.
     * Provides a stable `message` response shape so frontend code can display failures
     * consistently across validation, authorization, and domain-rule errors.
     */
    public static class ErrorResponse {
        private String message;

        public ErrorResponse(String message) {
            this.message = message;
        }

        
        
        public String getMessage() {
            return message;
        }
    }
}
