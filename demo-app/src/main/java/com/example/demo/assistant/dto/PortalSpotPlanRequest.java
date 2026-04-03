package com.example.demo.assistant.dto;

import jakarta.validation.constraints.NotBlank;

public record PortalSpotPlanRequest(
        @NotBlank String title,
        @NotBlank String location,
        String departureCity,
        Integer travelers,
        String startDate,
        String endDate,
        String budget,
        String preference
) {
}
