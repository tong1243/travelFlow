package com.example.demo.rag.dto;

import java.util.Map;

public record VectorSearchItem(
        String pointId,
        double score,
        Map<String, Object> payload
) {
}
