package com.example.demo.rag.service;

import com.example.demo.rag.dto.VectorSearchItem;
import com.example.demo.rag.dto.VectorSearchRequest;
import com.example.demo.rag.dto.VectorSearchResponse;
import com.example.demo.rag.dto.VectorUpsertRequest;
import com.example.demo.rag.dto.VectorizeResponse;
import com.example.demo.rag.model.VectorSearchHit;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class VectorService {

    private final EmbeddingService embeddingService;
    private final QdrantVectorStoreClient vectorStoreClient;

    public VectorService(EmbeddingService embeddingService, QdrantVectorStoreClient vectorStoreClient) {
        this.embeddingService = embeddingService;
        this.vectorStoreClient = vectorStoreClient;
    }

    public VectorizeResponse vectorize(String text) {
        List<Double> vector = embeddingService.vectorize(text);
        return new VectorizeResponse(vector.size(), vector);
    }

    public VectorSearchResponse search(VectorSearchRequest request) {
        int topK = request.topK() == null || request.topK() <= 0 ? 5 : request.topK();
        List<Double> queryVector = embeddingService.vectorize(request.query());
        List<VectorSearchHit> hits = vectorStoreClient.search(queryVector, topK);
        List<VectorSearchItem> items = hits.stream()
                .map(item -> new VectorSearchItem(item.pointId(), item.score(), item.payload()))
                .toList();
        return new VectorSearchResponse(items);
    }

    public String upsert(VectorUpsertRequest request) {
        List<Double> vector = embeddingService.vectorize(request.text());
        String pointId = (request.pointId() == null || request.pointId().isBlank())
                ? "manual-" + UUID.randomUUID()
                : request.pointId().trim();
        Map<String, Object> payload = new LinkedHashMap<>();
        if (request.payload() != null) {
            payload.putAll(request.payload());
        }
        payload.putIfAbsent("text", request.text());
        vectorStoreClient.upsert(pointId, vector, payload);
        return pointId;
    }
}
