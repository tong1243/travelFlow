package com.example.demo.rag.dto;

public record RagReferenceItem(
        Long chunkId,
        Long documentId,
        String documentTitle,
        double score,
        String snippet
) {
}
