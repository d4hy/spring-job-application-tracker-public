package com.example.jobtracker.core.mode;

/**
 * Domain-specific exception for offline-mode violations.
 * Raised when a feature that requires internet connectivity is invoked while the app
 * is configured to run in offline mode.
 */
public class OfflineModeException extends RuntimeException {
    public OfflineModeException(String message) {
        super(message);
    }
}
