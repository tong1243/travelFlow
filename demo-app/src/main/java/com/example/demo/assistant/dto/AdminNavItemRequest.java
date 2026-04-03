package com.example.demo.assistant.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminNavItemRequest(
        @NotBlank String label,
        Integer sortOrder,
        Boolean enabled
) {
}
