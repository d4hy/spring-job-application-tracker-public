package com.example.jobtracker.feature.integration.controller;

import com.example.jobtracker.feature.auth.model.entity.User;
import com.example.jobtracker.feature.auth.service.UserService;
import com.example.jobtracker.feature.integration.model.dto.WorkdayAutofillRequest;
import com.example.jobtracker.feature.integration.model.dto.WorkdayAutofillResponse;
import com.example.jobtracker.feature.integration.model.dto.WorkdayProfileRequest;
import com.example.jobtracker.feature.integration.model.dto.WorkdayProfileResponse;
import com.example.jobtracker.feature.integration.service.WorkdayAutofillService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for local job-form autofill workflows.
 * The /workday path is kept for compatibility with the existing frontend/backend contract.
 */
@RestController
@RequestMapping({"/api/integrations/workday", "/api/integrations/job-form"})
public class WorkdayIntegrationController {
    private final WorkdayAutofillService workdayAutofillService;
    private final UserService userService;

    public WorkdayIntegrationController(WorkdayAutofillService workdayAutofillService, UserService userService) {
        this.workdayAutofillService = workdayAutofillService;
        this.userService = userService;
    }

    @GetMapping("/profile")
    public ResponseEntity<WorkdayProfileResponse> getProfile() {
        User user = getCurrentUser();
        return ResponseEntity.ok(workdayAutofillService.getProfile(user));
    }

    @PutMapping("/profile")
    public ResponseEntity<WorkdayProfileResponse> upsertProfile(@RequestBody WorkdayProfileRequest request) {
        User user = getCurrentUser();
        return ResponseEntity.ok(workdayAutofillService.upsertProfile(user, request));
    }

    @PostMapping("/autofill")
    public ResponseEntity<?> startAutofill(@Valid @RequestBody WorkdayAutofillRequest request) {
        try {
            User user = getCurrentUser();
            WorkdayAutofillResponse response = workdayAutofillService.startAutofill(user, request);
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
     * Simple API error payload used by the Workday Integration endpoints.
     * Provides a stable `message` response shape so frontend code can display failures
     * consistently across validation, authorization, and domain-rule errors.
     */
    public static class ErrorResponse {
        private final String message;

        public ErrorResponse(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }
}
