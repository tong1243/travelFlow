package com.example.demo.rag.dto;

public record UserProfileResponse(
        Long userId,
        String username,
        String email,
        String role
) {
}
