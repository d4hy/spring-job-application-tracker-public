package com.example.jobtracker.feature.auth.controller;

import com.example.jobtracker.feature.auth.model.entity.User;
import com.example.jobtracker.feature.auth.service.JwtService;
import com.example.jobtracker.feature.auth.service.UserService;
import com.example.jobtracker.feature.auth.model.dto.AuthRequest;
import com.example.jobtracker.feature.auth.model.dto.AuthResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for Auth workflows.
 * Defines HTTP endpoints for this feature, resolves authenticated user context where
 * required, and delegates business orchestration to service-layer components.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService userService;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthController(UserService userService, JwtService jwtService, AuthenticationManager authenticationManager) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    /**
     * Register a new user
     * LEARNING NOTE: New user gets default "Job Hunt" board automatically
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody AuthRequest request) {
        try {
            User user = userService.registerUser(request.getUsername(), request.getPassword());
            String token = jwtService.generateToken(user.getUsername(), user.getId());
            return ResponseEntity.ok(new AuthResponse(token, user.getUsername()));
        
        
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * Login with username and password
     * LEARNING NOTE: AuthenticationManager handles password verification
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request) {
        try {
            // Authenticate user
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );

            // Get authenticated user details
            User user = userService.findByUsername(request.getUsername());

            // Generate JWT token
            String token = jwtService.generateToken(user.getUsername(), user.getId());

            return ResponseEntity.ok(new AuthResponse(token, user.getUsername()));
        
        
        } catch (AuthenticationException e) {
            return ResponseEntity.status(401).body(new ErrorResponse("Invalid credentials"));
        }
    }

    /**
     * Simple API error payload used by the Auth endpoints.
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

        
        
        public void setMessage(String message) {
            this.message = message;
        }
    }
}

