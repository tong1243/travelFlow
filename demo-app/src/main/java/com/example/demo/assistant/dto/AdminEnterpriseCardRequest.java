package com.example.demo.assistant.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminEnterpriseCardRequest(
        @NotBlank String title,
        @NotBlank String description,
        Integer sortOrder,
        Boolean enabled
) {
}
