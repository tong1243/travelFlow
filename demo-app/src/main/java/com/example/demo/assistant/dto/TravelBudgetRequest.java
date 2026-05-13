package com.example.demo.assistant.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record TravelBudgetRequest(
        @NotBlank(message = "目的地不能为空")
        String destination,
        @Min(value = 1, message = "天数至少为 1")
        Integer days,
        @Min(value = 1, message = "出行人数至少为 1")
        Integer travelers,
        @NotBlank(message = "预算币种不能为空，例如人民币")
        String budgetCurrency,
        String expectedBudget,
        String travelStyle,
        String notes
) {
}
