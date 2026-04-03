package com.example.demo.rag.dto;

public record AgentToolTrace(
        int step,
        String toolName,
        String toolInput,
        String toolOutputSummary
) {
}
