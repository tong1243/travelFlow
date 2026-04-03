package com.example.demo.assistant.dto;

public record AdminEnterpriseCardResponse(
        Long id,
        String title,
        String description,
        Integer sortOrder,
        boolean enabled
) {
}
