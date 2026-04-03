package com.example.demo.assistant.dto;

public record AdminNavItemResponse(
        Long id,
        String label,
        Integer sortOrder,
        boolean enabled
) {
}
