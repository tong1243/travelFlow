package com.example.demo.rag.dto;

import java.time.Instant;

public record KnowledgeDocumentResponse(
        Long documentId,
        String title,
        String sourceType,
        String sourceRef,
        String status,
        int versionNo,
        int chunkCount,
        Instant updatedAt
) {
}
