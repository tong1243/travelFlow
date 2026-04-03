package com.example.demo.rag.dto;

import jakarta.validation.constraints.NotBlank;

public record VectorizeRequest(
        @NotBlank String text
) {
}
