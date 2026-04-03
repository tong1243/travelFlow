package com.example.demo.assistant.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminCategoryRequest(
        @NotBlank String name,
        @NotBlank String keyword,
        Integer sortOrder,
        Boolean enabled
) {
}
