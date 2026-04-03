package com.example.demo.assistant.dto;

public record AdminSuggestionResponse(
        Long id,
        String value,
        Integer sortOrder,
        boolean enabled
) {
}
