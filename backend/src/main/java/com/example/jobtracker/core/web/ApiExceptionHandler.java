package com.example.jobtracker.core.web;

import com.example.jobtracker.core.mode.OfflineModeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global REST exception mapper for API controllers.
 * Converts domain and infrastructure exceptions into stable HTTP responses so clients
 * receive predictable error payloads regardless of where failures originate.
 */
@RestControllerAdvice
public class ApiExceptionHandler {
    
    
    @ExceptionHandler(OfflineModeException.class)
    public ResponseEntity<ErrorResponse> handleOfflineMode(OfflineModeException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse(exception.getMessage()));
    }

    /**
     * Simple API error payload used by the API Exception Handler endpoints.
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
