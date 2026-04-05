package com.example.demo.rag.dto;

import jakarta.validation.constraints.NotBlank;

public record AgentChatRequest(
        String sessionId,
        @NotBlank String question,
        Integer topK,
        String sourceType,
        String sourceRefContains,
        String toolMode,
        Boolean allowHighRiskTools,
        Boolean includeTrace
) {
}
