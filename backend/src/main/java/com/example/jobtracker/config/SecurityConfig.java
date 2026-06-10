package com.example.jobtracker.config;

import com.example.jobtracker.core.mode.OfflineModeSupport;
import com.example.jobtracker.feature.auth.service.JwtService;
import com.example.jobtracker.feature.auth.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Central Spring Security configuration for the API.
 * Defines authentication/authorization rules, JWT filter registration, and related
 * security infrastructure so requests follow one consistent protection model.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {
    /**
     * Builds the main HTTP security filter chain used by the backend.
     *
     * ` /api/auth/**`, OAuth callback paths, root, and H2 console are public.
     * All remaining routes require authentication, and API auth failures return `401` instead of redirects.
     *
     * @param http mutable Spring Security HTTP config object.
     * @param jwtAuthenticationFilter JWT filter inserted before username/password auth filter.
     * @param authenticationProvider auth provider backed by {@link UserService}.
     * @param oauth2UserService custom mapper for OAuth2 user info.
     * @param offlineModeSupport toggle controlling whether OAuth2 login is enabled.
     * @return configured {@link SecurityFilterChain} used at runtime.
     * @throws Exception when Spring cannot build the filter chain.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter,
                                                   AuthenticationProvider authenticationProvider,
                                                   CustomOAuth2UserService oauth2UserService,
                                                   OfflineModeSupport offlineModeSupport) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/integrations/google/callback").permitAll()
                .requestMatchers("/login/**").permitAll()
                .requestMatchers("/oauth2/**").permitAll()
                .requestMatchers("/h2-console/**").permitAll()
                .requestMatchers("/", "/favicon.ico").permitAll()
                .requestMatchers("/error").permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(exceptions -> exceptions
                .defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        new AntPathRequestMatcher("/api/**")
                )
            )
            .authenticationProvider(authenticationProvider)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .headers(headers -> headers.frameOptions(frame -> frame.disable()));

        if (!offlineModeSupport.isEnabled()) {
            http.oauth2Login(oauth2 -> oauth2
                    .userInfoEndpoint(userInfo -> userInfo
                            .userService(oauth2UserService)
                    )
            );
        }

        return http.build();
    }

    /**
     * Creates the JWT authentication filter that reads bearer tokens and loads users into security context.
     *
     * @param jwtService token parser/validator.
     * @param userService user lookup service.
     * @return initialized {@link JwtAuthenticationFilter}.
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwtService, UserService userService) {
        return new JwtAuthenticationFilter(jwtService, userService);
    }

    /**
     * Creates the authentication provider used for username/password login.
     *
     * @param userService Spring Security user details source.
     * @param passwordEncoder password hash verifier.
     * @return configured DAO authentication provider.
     */
    @Bean
    public AuthenticationProvider authenticationProvider(UserService userService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    /**
     * Exposes Spring's authentication manager from framework configuration.
     *
     * @param configuration framework auth configuration.
     * @return resolved {@link AuthenticationManager}.
     * @throws Exception when the manager cannot be created.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /**
     * Password encoder bean used for hashing and verifying passwords.
     *
     * @return BCrypt password encoder.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Builds CORS policy used for API routes.
     *
     * The value comes from `app.cors.allowed-origins` and supports comma-separated origin patterns.
     *
     * @param allowedOriginsProperty raw comma-separated origin pattern string from config.
     * @return CORS configuration source registered for `/api/**`.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:*,http://127.0.0.1:*,https://localhost:*,https://127.0.0.1:*}") String allowedOriginsProperty) {
        List<String> allowedOriginPatterns = Arrays.stream(allowedOriginsProperty.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(allowedOriginPatterns);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
