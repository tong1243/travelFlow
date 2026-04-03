package com.example.demo.assistant.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminSpotCardRequest(
        @NotBlank String title,
        @NotBlank String location,
        @NotBlank String price,
        @NotBlank String rating,
        @NotBlank String image,
        Integer sortOrder,
        Boolean enabled
) {
}
