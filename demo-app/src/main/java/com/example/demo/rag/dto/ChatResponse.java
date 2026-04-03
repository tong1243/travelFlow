package com.example.demo.rag.dto;

import java.util.List;

public record ChatResponse(
        String sessionId,
        String answer,
        String model,
        List<RagReferenceItem> references
) {
}
