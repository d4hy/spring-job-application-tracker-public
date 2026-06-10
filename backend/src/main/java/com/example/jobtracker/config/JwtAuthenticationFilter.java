package com.example.jobtracker.config;

import com.example.jobtracker.feature.auth.service.JwtService;
import com.example.jobtracker.feature.auth.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Request filter that performs JWT-based authentication for incoming API calls.
 * Extracts bearer tokens, validates claims, and populates the Spring Security context
 * so downstream handlers can enforce user-scoped authorization rules.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserService userService;

    public JwtAuthenticationFilter(JwtService jwtService, UserService userService) {
        this.jwtService = jwtService;
        this.userService = userService;
    }

    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            // Extract JWT token from Authorization header
            
            String authHeader = request.getHeader("Authorization");
            String token = null;
            String username = null;

            // Check if header exists and starts with "Bearer "
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                
                token = authHeader.substring(7);
                
                username = jwtService.extractUsername(token);
            }

            // If token is valid and no user is already authenticated in context
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                if (jwtService.isTokenValid(token)) {
                    // Load user details and create authentication token
                    
                    UserDetails userDetails = userService.loadUserByUsername(username);

                    UsernamePasswordAuthenticationToken authenticationToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );

                    // Set request details for audit trail
                    authenticationToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );

                    // Store authentication in SecurityContext
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                }
            }
        
        
        } catch (Exception e) {
            // Log but don't fail - let request continue to security layer which will handle auth errors
            
            logger.debug("Cannot set user authentication in security context", e);
        }

        
        filterChain.doFilter(request, response);
    }
}

