package com.example.demo.assistant.dto;

public record SystemStatusResponse(
        boolean apiKeyConfigured,
        String baseUrl,
        String defaultModel,
        String fileModel,
        String fallbackModel,
        Integer maxTokens
) {
}
