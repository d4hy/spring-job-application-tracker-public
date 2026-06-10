package com.example.jobtracker.feature.tracking.repository;

import com.example.jobtracker.feature.tracking.model.entity.Board;
import com.example.jobtracker.feature.auth.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BoardRepository extends JpaRepository<Board, Long> {
    
    
    List<Board> findByUser(User user);
    
    
    Optional<Board> findByIdAndUser(Long id, User user);
    
    
    Optional<Board> findByNameAndUser(String name, User user);
}

