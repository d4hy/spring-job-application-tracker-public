package com.example.jobtracker.core.mode;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Infrastructure helper for the project's offline-mode behavior.
 * Exposes offline-state checks and guard methods used by services to block online-only
 * operations with consistent, user-facing error semantics.
 */
@Component
public class OfflineModeSupport {
    private final boolean offlineModeEnabled;

    public OfflineModeSupport(@Value("${app.offline-mode:false}") boolean offlineModeEnabled) {
        this.offlineModeEnabled = offlineModeEnabled;
    }

    
    
    public boolean isEnabled() {
        return offlineModeEnabled;
    }

    
    
    public void requireOnline(String featureName) {
        if (offlineModeEnabled) {
            throw new OfflineModeException(featureName + " is disabled because offline mode is enabled.");
        }
    }
}
