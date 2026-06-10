package com.example.jobtracker.feature.tracking.repository;

import com.example.jobtracker.feature.tracking.model.entity.JobApplication;
import com.example.jobtracker.feature.tracking.model.entity.BoardColumn;
import com.example.jobtracker.feature.auth.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {
    
    
    List<JobApplication> findByColumn(BoardColumn column);
    
    
    Optional<JobApplication> findByIdAndColumnBoardUserId(Long id, Long userId);
    
    
    List<JobApplication> findByColumnOrderByOrderIndexAsc(BoardColumn column);
    
    
    Optional<JobApplication> findFirstByColumn_Board_UserAndJobUrl(User user, String jobUrl);
    
    
    List<JobApplication> findByColumn_Board_UserOrderByCreatedAtDesc(User user);
}

