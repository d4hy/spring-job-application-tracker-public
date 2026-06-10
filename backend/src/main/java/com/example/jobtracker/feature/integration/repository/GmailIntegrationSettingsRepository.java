package com.example.jobtracker.feature.integration.repository;

import com.example.jobtracker.feature.integration.model.entity.GmailIntegrationSettings;
import com.example.jobtracker.feature.auth.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GmailIntegrationSettingsRepository extends JpaRepository<GmailIntegrationSettings, Long> {
    
    
    Optional<GmailIntegrationSettings> findByUser(User user);
    
    
    List<GmailIntegrationSettings> findByConnectedTrueAndAutoSyncEnabledTrue();
}

