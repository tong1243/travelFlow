package com.example.demo.rag.dto;

import jakarta.validation.constraints.NotBlank;

public record VectorSearchRequest(
        @NotBlank String query,
        Integer topK
) {
}
