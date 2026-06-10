package com.example.jobtracker.feature.integration.repository;

import com.example.jobtracker.feature.auth.model.entity.User;
import com.example.jobtracker.feature.integration.model.entity.WorkdayProfileSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WorkdayProfileSettingsRepository extends JpaRepository<WorkdayProfileSettings, Long> {
    Optional<WorkdayProfileSettings> findByUser(User user);
}
