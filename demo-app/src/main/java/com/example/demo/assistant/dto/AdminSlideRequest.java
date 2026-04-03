package com.example.demo.assistant.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminSlideRequest(
        @NotBlank String title,
        @NotBlank String subtitle,
        @NotBlank String description,
        @NotBlank String image,
        Integer sortOrder,
        Boolean enabled
) {
}
