package com.example.demo.rag.dto;

import java.time.Instant;

public record ConversationSummaryResponse(
        String sessionId,
        String title,
        Instant updatedAt
) {
}
