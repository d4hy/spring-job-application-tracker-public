package com.example.jobtracker.config;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/**
 * Spring Security OAuth user-info adapter for external login providers.
 * Loads provider profile attributes after OAuth sign-in and provides the extension
 * point where provider identity is mapped to local user-account state.
 */
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    
    
    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        // Get user info from provider (Google, GitHub, etc.)
        
        OAuth2User oAuth2User = super.loadUser(userRequest);

        // LEARNING NOTE: Here you could:
        // 1. Get provider name (google, github, etc.)
        // 2. Extract user attributes (email, name, picture)
        // 3. Save or update user in database
        // 4. Set user roles/authorities

        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        return oAuth2User;
    }
}
