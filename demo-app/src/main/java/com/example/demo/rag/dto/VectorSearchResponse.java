package com.example.demo.rag.dto;

import java.util.List;

public record VectorSearchResponse(
        List<VectorSearchItem> items
) {
}
