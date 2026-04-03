package com.example.demo.rag.dto;

import java.time.Instant;

public record ConversationMessageResponse(
        Long id,
        String role,
        String content,
        Instant createdAt
) {
}
