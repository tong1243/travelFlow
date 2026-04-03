package com.example.demo.rag.dto;

public record AuthResponse(
        Long userId,
        String username,
        String token,
        long expiresInSeconds
) {
}
