package com.example.demo.rag.dto;

import java.util.List;

public record AgentChatResponse(
        String sessionId,
        String answer,
        String model,
        List<RagReferenceItem> references,
        List<AgentToolTrace> traces
) {
}
