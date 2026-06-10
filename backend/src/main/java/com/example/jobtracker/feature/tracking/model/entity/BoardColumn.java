package com.example.jobtracker.feature.tracking.model.entity;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA entity representing Board Column in the persistence model.
 * Maps domain state and relationships to relational storage so repository and service
 * layers can work with strongly typed, lifecycle-managed objects.
 */
@Entity
@Table(name = "columns")
public class BoardColumn {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int orderIndex;

    @ManyToOne(optional = false)
    private Board board;

    @OneToMany(mappedBy = "column", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    private List<JobApplication> jobs = new ArrayList<>();

    public BoardColumn() {
    }

    public BoardColumn(String name, int orderIndex, Board board) {
        this.name = name;
        this.orderIndex = orderIndex;
        this.board = board;
    }

    
    
    public Long getId() {
        return id;
    }

    
    
    public String getName() {
        return name;
    }

    
    
    public void setName(String name) {
        this.name = name;
    }

    
    
    public int getOrderIndex() {
        return orderIndex;
    }

    
    
    public void setOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }

    
    
    public Board getBoard() {
        return board;
    }

    
    
    public void setBoard(Board board) {
        this.board = board;
    }

    
    
    public List<JobApplication> getJobs() {
        return jobs;
    }
}
