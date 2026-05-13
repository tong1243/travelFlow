package com.example.demo.rag.dto;

import jakarta.validation.constraints.NotBlank;

public record AgentChatRequest(
        String sessionId,
        @NotBlank String question,
        Integer topK,
        String sourceType,
        String sourceRefContains,
        Boolean allowHighRiskTools,
        Boolean includeTrace,
        String travelMode,
        Boolean hotelRecommendation,
        String hotelPreference,
        String hotelPriceRange,
        Boolean weatherQuery,
        String departureCity,
        String destinationCity,
        String travelStartDate,
        String travelEndDate,
        Integer travelers,
        String budget,
        String companionType,
        String travelStyle
) {
}
