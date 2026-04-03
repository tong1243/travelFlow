package com.example.demo.assistant.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record TravelBudgetRequest(
        @NotBlank(message = "destination 不能为空")
        String destination,
        @Min(value = 1, message = "days 至少为 1")
        Integer days,
        @Min(value = 1, message = "travelers 至少为 1")
        Integer travelers,
        @NotBlank(message = "budgetCurrency 不能为空，例如 CNY")
        String budgetCurrency,
        String expectedBudget,
        String travelStyle,
        String notes
) {
}
