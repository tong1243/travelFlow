package com.example.demo.assistant.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record TravelPlanRequest(
        @NotBlank(message = "destination 不能为空")
        String destination,
        @NotBlank(message = "startDate 不能为空，例如 2026-05-01")
        String startDate,
        @NotBlank(message = "endDate 不能为空，例如 2026-05-05")
        String endDate,
        @Min(value = 1, message = "travelers 至少为 1")
        Integer travelers,
        String departureCity,
        String budget,
        String interests,
        String travelStyle,
        String notes
) {
}
