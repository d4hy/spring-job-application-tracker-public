package com.example.jobtracker.feature.tracking.repository;

import com.example.jobtracker.feature.tracking.model.entity.BoardColumn;
import com.example.jobtracker.feature.tracking.model.entity.Board;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BoardColumnRepository extends JpaRepository<BoardColumn, Long> {
    
    
    List<BoardColumn> findByBoard(Board board);
    
    
    Optional<BoardColumn> findByIdAndBoard(Long id, Board board);
}

