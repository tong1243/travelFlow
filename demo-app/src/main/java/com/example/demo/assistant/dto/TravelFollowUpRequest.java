package com.example.demo.assistant.dto;

import jakarta.validation.constraints.NotBlank;

public record TravelFollowUpRequest(
        @NotBlank String previousAnswer,
        @NotBlank String question
) {
}
