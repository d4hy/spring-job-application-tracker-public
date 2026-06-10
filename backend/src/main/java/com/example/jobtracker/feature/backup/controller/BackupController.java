package com.example.jobtracker.feature.backup.controller;

import com.example.jobtracker.feature.auth.model.entity.User;
import com.example.jobtracker.feature.auth.service.UserService;
import com.example.jobtracker.feature.backup.model.dto.BackupCloudStatusResponse;
import com.example.jobtracker.feature.backup.model.dto.BackupCloudUploadResponse;
import com.example.jobtracker.feature.backup.model.dto.BackupGoogleSheetUploadRequest;
import com.example.jobtracker.feature.backup.model.dto.BackupGoogleSheetUploadResponse;
import com.example.jobtracker.feature.backup.model.dto.BackupImportResponse;
import com.example.jobtracker.feature.backup.model.dto.TrackerBackupPayload;
import com.example.jobtracker.feature.backup.service.BackupService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for Backup workflows.
 * Defines HTTP endpoints for this feature, resolves authenticated user context where
 * required, and delegates business orchestration to service-layer components.
 */
@RestController
@RequestMapping("/api/backups")
public class BackupController {
    private final BackupService backupService;
    private final UserService userService;

    public BackupController(BackupService backupService, UserService userService) {
        this.backupService = backupService;
        this.userService = userService;
    }

    
    
    @GetMapping("/export")
    public ResponseEntity<TrackerBackupPayload> exportBackup() {
        
        User user = getCurrentUser();
        return ResponseEntity.ok(backupService.exportBackup(user));
    }

    
    
    @PostMapping("/import")
    public ResponseEntity<?> importBackup(@RequestBody TrackerBackupPayload payload) {
        try {
            
            User user = getCurrentUser();
            
            BackupImportResponse response = backupService.importBackup(user, payload);
            return ResponseEntity.ok(response);
        
        
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    
    
    @GetMapping("/cloud-status")
    public ResponseEntity<BackupCloudStatusResponse> cloudStatus() {
        return ResponseEntity.ok(backupService.cloudStatus());
    }

    
    
    @PostMapping("/cloud-upload")
    public ResponseEntity<?> uploadToCloud() {
        try {
            
            User user = getCurrentUser();
            
            BackupCloudUploadResponse response = backupService.uploadBackupToCloud(user);
            return ResponseEntity.ok(response);
        
        
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    
    
    @PostMapping("/google-sheet-upload")
    public ResponseEntity<?> uploadToGoogleSheet(@RequestBody BackupGoogleSheetUploadRequest request) {
        try {
            
            User user = getCurrentUser();
            
            BackupGoogleSheetUploadResponse response = backupService.uploadBackupToGoogleSheet(user, request);
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
     * Simple API error payload used by the Backup endpoints.
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
