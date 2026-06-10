package com.example.jobtracker.feature.auth.service;

import com.example.jobtracker.feature.auth.model.entity.User;
import com.example.jobtracker.feature.auth.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Service-layer component for User workflows.
 * Centralizes business rules, validation, and orchestration across repositories and
 * external integrations so controllers remain focused on request/response handling.
 */
@Service
public class UserService implements UserDetailsService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Creates the service with repository and password hashing dependencies.
     *
     * @param userRepository persistence access for users.
     * @param passwordEncoder password hash encoder/verifier.
     */
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Loads a user for Spring Security's authentication flow.
     *
     * Converts the app's comma-separated role string into GrantedAuthority objects and
     * returns Spring's framework-specific {@link UserDetails} implementation.
     *
     * @param username login username (email in OAuth-backed accounts).
     * @return populated {@link UserDetails} instance for authentication checks.
     * @throws UsernameNotFoundException when no user exists for the provided username.
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        var authorities = Arrays.stream(user.getRoles().split(","))
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPasswordHash())
                .authorities(authorities)
                .build();
    }

    /**
     * Registers a brand-new local user account.
     *
     * Passwords are never stored in plain text; they are encoded before persistence.
     *
     * @param username desired unique username.
     * @param password raw password from registration form.
     * @return newly saved {@link User} entity.
     * @throws IllegalArgumentException when username already exists.
     */
    public User registerUser(String username, String password) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRoles("ROLE_USER");
        return userRepository.save(user);
    }

    /**
     * Looks up a user by database ID.
     *
     * @param id user primary key.
     * @return matching {@link User}.
     * @throws UsernameNotFoundException when user ID does not exist.
     */
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + id));
    }

    /**
     * Looks up a user by username.
     *
     * @param username unique username.
     * @return matching {@link User}.
     * @throws UsernameNotFoundException when username does not exist.
     */
    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    /**
     * Finds an existing OAuth user or creates a new local record for first-time OAuth login.
     *
     * The OAuth email is used as username to keep identity mapping straightforward.
     *
     * @param email email from OAuth provider profile.
     * @param name display name from OAuth provider profile (currently not persisted).
     * @param provider provider key, e.g. `google`/`github`.
     * @return existing or newly created {@link User}.
     */
    public User findOrCreateOAuth2User(String email, String name, String provider) {
        var existingUser = userRepository.findByUsername(email);
        if (existingUser.isPresent()) {
            return existingUser.get();
        }

        User user = new User();
        user.setUsername(email);
        user.setPasswordHash("oauth2:" + provider);
        user.setRoles("ROLE_USER");
        return userRepository.save(user);
    }

    /**
     * Verifies a raw password against an encoded password hash.
     *
     * @param rawPassword plain text password from login request.
     * @param encodedPassword stored password hash.
     * @return `true` when password matches; otherwise `false`.
     */
    public boolean validatePassword(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
}
