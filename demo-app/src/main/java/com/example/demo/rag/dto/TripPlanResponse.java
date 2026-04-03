package com.example.demo.rag.dto;

import java.time.Instant;
import java.time.LocalDate;

public record TripPlanResponse(
        Long id,
        String title,
        String keyword,
        String summary,
        String answer,
        String departureCity,
        Integer travelers,
        LocalDate startDate,
        LocalDate endDate,
        String budget,
        String companionType,
        String travelStyle,
        Instant createdAt,
        Instant updatedAt
) {
}
