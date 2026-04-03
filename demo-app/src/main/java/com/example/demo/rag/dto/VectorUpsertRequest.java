package com.example.demo.rag.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record VectorUpsertRequest(
        String pointId,
        @NotBlank String text,
        Map<String, Object> payload
) {
}
