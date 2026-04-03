package com.example.demo.rag.model;

public record HybridSearchHit(
        Long chunkId,
        Long documentId,
        String documentTitle,
        String sourceType,
        String sourceRef,
        String snippet,
        double vectorScore,
        double lexicalScore,
        double rerankScore,
        double score
) {
}
