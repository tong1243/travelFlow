package com.example.demo.assistant.dto;

import java.time.Instant;

public record ErrorResponse(
        String message,
        Instant timestamp
) {
}
