package com.example.demo.assistant.dto;

public record AdminSlideResponse(
        Long id,
        String title,
        String subtitle,
        String description,
        String image,
        int sortOrder,
        boolean enabled
) {
}
