package com.example.demo.assistant.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record TravelPlanRequest(
        @NotBlank(message = "目的地不能为空")
        String destination,
        @NotBlank(message = "开始日期不能为空，例如 2026-05-01")
        String startDate,
        @NotBlank(message = "结束日期不能为空，例如 2026-05-05")
        String endDate,
        @Min(value = 1, message = "出行人数至少为 1")
        Integer travelers,
        String departureCity,
        String budget,
        String interests,
        String travelStyle,
        String notes
) {
}
