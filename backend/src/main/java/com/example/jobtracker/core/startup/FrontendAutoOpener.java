package com.example.jobtracker.core.startup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.awt.Desktop;
import java.net.URI;
import java.util.Locale;

/**
 * Startup utility that can open the frontend URL in a browser.
 * Runs after backend initialization and improves local development ergonomics by
 * automating the initial handoff from backend startup to UI access.
 */
@Component
@ConditionalOnProperty(name = "app.frontend.auto-open", havingValue = "true", matchIfMissing = true)
public class FrontendAutoOpener {
    
    private static final Logger logger = LoggerFactory.getLogger(FrontendAutoOpener.class);
    private final String frontendBaseUrl;

    public FrontendAutoOpener(@Value("${app.frontend.base-url:http://localhost:5173}") String frontendBaseUrl) {
        this.frontendBaseUrl = frontendBaseUrl;
    }

    
    
    @EventListener(ApplicationReadyEvent.class)
    public void openFrontend() {
        
        String normalizedBaseUrl = normalizeBaseUrl(frontendBaseUrl);
        if (!isLoopbackUrl(normalizedBaseUrl)) {
            
            logger.info("Skipping frontend auto-open for non-local URL: {}", normalizedBaseUrl);
            return;
        }

        if (!Desktop.isDesktopSupported()) {
            
            logger.info("Desktop integration not supported; skipping frontend auto-open.");
            return;
        }

        
        Desktop desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.BROWSE)) {
            
            logger.info("Desktop browse action is not supported; skipping frontend auto-open.");
            return;
        }

        try {
            desktop.browse(URI.create(normalizedBaseUrl));
            
            logger.info("Opened frontend in browser: {}", normalizedBaseUrl);
        
        
        } catch (Exception exception) {
            
            logger.warn("Failed to auto-open frontend URL: {}", normalizedBaseUrl, exception);
        }
    }

    
    
    private String normalizeBaseUrl(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "http://localhost:5173";
        }
        
        String trimmed = value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    
    
    private boolean isLoopbackUrl(String value) {
        try {
            
            URI uri = URI.create(value);
            
            String host = uri.getHost();
            if (host == null) {
                return false;
            }

            
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            return "localhost".equals(normalizedHost)
                    || "127.0.0.1".equals(normalizedHost)
                    || "0.0.0.0".equals(normalizedHost)
                    
                    || "::1".equals(normalizedHost);
        
        
        } catch (IllegalArgumentException exception) {
            
            logger.warn("Invalid frontend URL configured for auto-open: {}", value);
            return false;
        }
    }
}
