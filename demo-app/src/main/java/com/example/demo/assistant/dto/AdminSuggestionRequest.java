package com.example.demo.assistant.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminSuggestionRequest(
        @NotBlank String value,
        Integer sortOrder,
        Boolean enabled
) {
}
