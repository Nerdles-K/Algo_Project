package com.synchplay.auth.dto;

public record AuthResponse(String token, UserView user) {
    public record UserView(Long id, String username, String email, String graphNodeId, String role) {}
}
