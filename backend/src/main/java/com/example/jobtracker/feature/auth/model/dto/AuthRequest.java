package com.example.jobtracker.feature.auth.model.dto;

/**
 * Request DTO for Auth operations.
 * Represents client-provided input fields for API endpoints while keeping transport
 * contracts decoupled from internal entities and persistence models.
 */
public class AuthRequest {
    private String username;
    private String password;

    public AuthRequest() {
    }

    public AuthRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }

    
    
    public String getUsername() {
        return username;
    }

    
    
    public void setUsername(String username) {
        this.username = username;
    }

    
    
    public String getPassword() {
        return password;
    }

    
    
    public void setPassword(String password) {
        this.password = password;
    }
}
