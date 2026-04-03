package com.example.demo.rag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KnowledgeUpsertRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank String content,
        @Size(max = 64) String sourceType,
        @Size(max = 255) String sourceRef
) {
}
