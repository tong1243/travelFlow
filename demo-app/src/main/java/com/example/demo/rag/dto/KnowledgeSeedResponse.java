package com.example.demo.rag.dto;

public record KnowledgeSeedResponse(
        int total,
        int created,
        int updated,
        int skipped
) {
}
