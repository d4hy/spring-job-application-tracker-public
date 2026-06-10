package com.example.jobtracker.feature.tracking.model.entity;

import com.example.jobtracker.feature.auth.model.entity.User;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA entity representing Board in the persistence model.
 * Maps domain state and relationships to relational storage so repository and service
 * layers can work with strongly typed, lifecycle-managed objects.
 */
@Entity
@Table(name = "boards")
public class Board {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(optional = false)
    private User user;

    @OneToMany(mappedBy = "board", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    private List<com.example.jobtracker.feature.tracking.model.entity.BoardColumn> columns = new ArrayList<>();

    public Board() {
    }

    public Board(String name, User user) {
        this.name = name;
        this.user = user;
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

    
    
    public User getUser() {
        return user;
    }

    
    
    public void setUser(User user) {
        this.user = user;
    }

    
    
    public List<com.example.jobtracker.feature.tracking.model.entity.BoardColumn> getColumns() {
        return columns;
    }
}
