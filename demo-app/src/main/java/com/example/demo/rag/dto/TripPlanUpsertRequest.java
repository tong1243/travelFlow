package com.example.demo.rag.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record TripPlanUpsertRequest(
        @NotBlank String title,
        @NotBlank String keyword,
        String summary,
        @NotBlank String answer,
        @NotBlank String departureCity,
        @NotNull @Min(1) Integer travelers,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotBlank String budget,
        @NotBlank String companionType,
        @NotBlank String travelStyle
) {
}
