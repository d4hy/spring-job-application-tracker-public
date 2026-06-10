package com.example.jobtracker.feature.integration.repository;

import com.example.jobtracker.feature.integration.model.entity.GoogleOAuthState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GoogleOAuthStateRepository extends JpaRepository<GoogleOAuthState, Long> {
    
    
    Optional<GoogleOAuthState> findByStateToken(String stateToken);
}

