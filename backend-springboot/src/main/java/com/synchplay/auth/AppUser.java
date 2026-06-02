package com.synchplay.auth;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(nullable = false, unique = true, length = 128)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 72)
    private String passwordHash;

    @Column(name = "graph_node_id", nullable = false, length = 64)
    private String graphNodeId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false, length = 16)
    private String role = "USER";

    protected AppUser() {}

    public AppUser(String username, String email, String passwordHash, String graphNodeId) {
        this(username, email, passwordHash, graphNodeId, "USER");
    }

    public AppUser(String username, String email, String passwordHash, String graphNodeId, String role) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.graphNodeId = graphNodeId;
        this.role = role;
    }

    public Long getId()            { return id; }
    public String getUsername()    { return username; }
    public String getEmail()       { return email; }
    public String getPasswordHash(){ return passwordHash; }
    public String getGraphNodeId() { return graphNodeId; }
    public String getRole()        { return role; }
    public void setRole(String role) { this.role = role; }
    public Instant getCreatedAt()  { return createdAt; }

    public boolean isAdmin()       { return "ADMIN".equals(role); }
}
