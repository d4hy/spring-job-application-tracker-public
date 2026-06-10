package com.example.jobtracker.config;

import org.springframework.context.annotation.Configuration;

/**
 * Configuration placeholder for OAuth2 login-success behavior.
 * Keeps a dedicated extension point for custom post-login handling, such as token
 * handoff or redirect workflows, without coupling those concerns to core config.
 */
@Configuration
public class OAuth2LoginSuccessHandler {
}
