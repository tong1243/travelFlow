package com.example.demo.assistant.dto;

public record AdminGuideCardResponse(
        Long id,
        String title,
        String cover,
        String reads,
        int sortOrder,
        boolean enabled
) {
}
