package com.example.demo.rag.model;

import java.util.Map;

public record VectorSearchHit(
        String pointId,
        double score,
        Map<String, Object> payload
) {
}
