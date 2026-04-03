package com.example.demo.assistant.dto;

public record AdminCategoryResponse(
        Long id,
        String name,
        String keyword,
        Integer sortOrder,
        boolean enabled
) {
}
