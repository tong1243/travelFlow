package com.example.demo.rag.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
        String sessionId,
        @NotBlank String question,
        Integer topK
) {
}
