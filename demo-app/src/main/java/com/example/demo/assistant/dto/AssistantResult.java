package com.example.demo.assistant.dto;

public record AssistantResult(
        String mode,
        String model,
        String content
) {
}
