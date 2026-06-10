package com.example.jobtracker.feature.auth.model.entity;

import com.example.jobtracker.feature.tracking.model.entity.Board;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA entity representing User in the persistence model.
 * Maps domain state and relationships to relational storage so repository and service
 * layers can work with strongly typed, lifecycle-managed objects.
 */
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String roles;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Board> boards = new ArrayList<>();

    public User() {
    }

    public User(String username, String passwordHash, String roles) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.roles = roles;
    }

    
    
    public Long getId() {
        return id;
    }

    
    
    public String getUsername() {
        return username;
    }

    
    
    public void setUsername(String username) {
        this.username = username;
    }

    
    
    public String getPasswordHash() {
        return passwordHash;
    }

    
    
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    
    
    public String getRoles() {
        return roles;
    }

    
    
    public void setRoles(String roles) {
        this.roles = roles;
    }

    
    
    public List<Board> getBoards() {
        return boards;
    }
}
