package com.example.jobtracker.feature.integration.service;

import com.example.jobtracker.core.mode.OfflineModeSupport;
import com.example.jobtracker.feature.integration.model.entity.GmailIntegrationSettings;
import com.example.jobtracker.feature.integration.model.entity.GoogleOAuthState;
import com.example.jobtracker.feature.auth.model.entity.User;
import com.example.jobtracker.feature.integration.repository.GmailIntegrationSettingsRepository;
import com.example.jobtracker.feature.integration.repository.GoogleOAuthStateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Service-layer component for Google Integration workflows.
 * Centralizes business rules, validation, and orchestration across repositories and
 * external integrations so controllers remain focused on request/response handling.
 */
@Service
public class GoogleIntegrationService {
    private final GoogleOAuthStateRepository oAuthStateRepository;
    private final GmailIntegrationSettingsRepository settingsRepository;
    private final GoogleOAuthClient googleOAuthClient;
    private final OfflineModeSupport offlineModeSupport;
    
    private final SecureRandom secureRandom = new SecureRandom();

    public GoogleIntegrationService(GoogleOAuthStateRepository oAuthStateRepository,
                                    GmailIntegrationSettingsRepository settingsRepository,
                                    GoogleOAuthClient googleOAuthClient,
                                    OfflineModeSupport offlineModeSupport) {
        this.oAuthStateRepository = oAuthStateRepository;
        this.settingsRepository = settingsRepository;
        this.googleOAuthClient = googleOAuthClient;
        this.offlineModeSupport = offlineModeSupport;
    }

    
    
    @Transactional
    public String createAuthorizationUrl(User user) {
        
        offlineModeSupport.requireOnline("Google OAuth integration");

        
        String stateToken = generateStateToken();

        
        GoogleOAuthState state = new GoogleOAuthState();
        
        state.setUser(user);
        
        state.setStateToken(stateToken);
        
        state.setConsumed(false);
        state.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        
        oAuthStateRepository.save(state);

        return googleOAuthClient.buildAuthorizationUrl(stateToken);
    }

    
    
    @Transactional
    public void completeOAuthCallback(String code, String stateToken) {
        
        offlineModeSupport.requireOnline("Google OAuth integration");

        if (isBlank(code) || isBlank(stateToken)) {
            throw new IllegalArgumentException("Missing OAuth callback parameters");
        }

        GoogleOAuthState state = oAuthStateRepository.findByStateToken(stateToken)
                .orElseThrow(() -> new IllegalArgumentException("Invalid OAuth state"));

        if (state.isConsumed()) {
            throw new IllegalArgumentException("OAuth state was already used");
        }
        if (state.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("OAuth state has expired");
        }

        
        state.setConsumed(true);
        
        oAuthStateRepository.save(state);

        
        GoogleOAuthClient.OAuthTokenResponse token = googleOAuthClient.exchangeAuthorizationCode(code);
        
        String accessToken = token.accessToken();
        
        String refreshToken = token.refreshToken();
        
        String email = googleOAuthClient.fetchUserEmail(accessToken);

        GmailIntegrationSettings settings = settingsRepository.findByUser(state.getUser())
                
                .orElseGet(GmailIntegrationSettings::new);

        settings.setUser(state.getUser());
        
        settings.setGmailAddress(email);
        if (!isBlank(refreshToken)) {
            
            settings.setAppPassword(refreshToken);
        } else if (isBlank(settings.getAppPassword())) {
            throw new IllegalArgumentException("Google did not return a refresh token. Reconnect with consent prompt.");
        }
        
        settings.setConnected(true);
        
        settingsRepository.save(settings);
    }

    
    
    private String generateStateToken() {
        byte[] bytes = new byte[32];
        
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    
    
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
