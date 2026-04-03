package com.example.demo.assistant.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminGuideCardRequest(
        @NotBlank String title,
        @NotBlank String cover,
        @NotBlank String reads,
        Integer sortOrder,
        Boolean enabled
) {
}
