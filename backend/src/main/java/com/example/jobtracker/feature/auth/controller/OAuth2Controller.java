package com.example.jobtracker.feature.auth.controller;

import com.example.jobtracker.feature.auth.model.dto.AuthResponse;
import com.example.jobtracker.feature.auth.model.entity.User;
import com.example.jobtracker.feature.auth.service.JwtService;
import com.example.jobtracker.feature.auth.service.UserService;
import com.example.jobtracker.feature.tracking.service.BoardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for O Auth2 workflows.
 * Defines HTTP endpoints for this feature, resolves authenticated user context where
 * required, and delegates business orchestration to service-layer components.
 */
@RestController
@RequestMapping("/api/oauth2")
public class OAuth2Controller {
    private final UserService userService;
    private final JwtService jwtService;
    private final BoardService boardService;

    /**
     * Creates the OAuth2 controller with required services.
     *
     * @param userService user lookup/create service for OAuth accounts.
     * @param jwtService token service used to mint API bearer tokens.
     * @param boardService board service retained for future OAuth onboarding flows.
     */
    public OAuth2Controller(UserService userService, JwtService jwtService, BoardService boardService) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.boardService = boardService;
    }

    /**
     * Returns a JWT for the currently authenticated OAuth2 user.
     *
     * This endpoint expects the OAuth login to have completed already. If the user is not
     * authenticated, it returns `401`. Otherwise it maps OAuth profile fields to a local user
     * and returns a token payload usable by the frontend.
     *
     * @return `200` with {@link AuthResponse} on success, otherwise an error response.
     */
    @GetMapping("/user")
    public ResponseEntity<?> getCurrentOAuth2User() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(new ErrorResponse("Not authenticated"));
        }

        try {
            OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
            String email = oauth2User.getAttribute("email");
            String name = oauth2User.getAttribute("name");
            String provider = extractProvider();

            User user = userService.findOrCreateOAuth2User(email, name, provider);
            String token = jwtService.generateToken(user.getUsername(), user.getId());
            return ResponseEntity.ok(new AuthResponse(token, user.getUsername()));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("OAuth2 authentication failed: " + e.getMessage()));
        }
    }

    /**
     * Resolves provider identifier from the current authentication context.
     *
     * @return provider key for persistence/audit, currently `"oauth2"`.
     */
    private String extractProvider() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getName() != null) {
            return "oauth2";
        }
        return "oauth2";
    }

    /**
     * Simple API error payload used by the O Auth2 endpoints.
     * Provides a stable `message` response shape so frontend code can display failures
     * consistently across validation, authorization, and domain-rule errors.
     */
    public static class ErrorResponse {
        private String message;

        /**
         * Creates an error payload.
         *
         * @param message error message shown to API clients.
         */
        public ErrorResponse(String message) {
            this.message = message;
        }

        /**
         * Returns the current error message.
         *
         * @return stored error message.
         */
        public String getMessage() {
            return message;
        }

        /**
         * Updates the error message.
         *
         * @param message new message value.
         * @return void.
         */
        public void setMessage(String message) {
            this.message = message;
        }
    }
}
