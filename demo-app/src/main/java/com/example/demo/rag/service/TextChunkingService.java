package com.example.demo.rag.service;

import com.example.demo.rag.config.RagPipelineProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TextChunkingService {

    private final RagPipelineProperties properties;

    public TextChunkingService(RagPipelineProperties properties) {
        this.properties = properties;
    }

    public List<String> splitToChunks(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        int chunkSize = Math.max(120, properties.getChunkSize());
        int overlap = Math.max(0, Math.min(properties.getChunkOverlap(), chunkSize - 1));
        int step = Math.max(1, chunkSize - overlap);

        String normalized = content.replace("\r\n", "\n").trim();
        List<String> chunks = new ArrayList<>();

        for (int start = 0; start < normalized.length(); start += step) {
            int end = Math.min(start + chunkSize, normalized.length());
            String chunk = normalized.substring(start, end).trim();
            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }
            if (end >= normalized.length()) {
                break;
            }
        }
        return chunks;
    }
}
