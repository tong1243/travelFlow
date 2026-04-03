package com.example.demo.rag.service;

import com.example.demo.rag.RagException;
import com.example.demo.rag.config.VectorDbProperties;
import com.example.demo.rag.model.VectorSearchHit;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class QdrantVectorStoreClient {

    private static final Logger log = LoggerFactory.getLogger(QdrantVectorStoreClient.class);

    private final VectorDbProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public QdrantVectorStoreClient(VectorDbProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    @PostConstruct
    public void initCollection() {
        if (properties.isCreateIfMissing()) {
            try {
                ensureCollection();
            } catch (RagException ex) {
                log.warn("Qdrant is unavailable during startup. RAG vector features will fail until Qdrant is reachable. {}", ex.getMessage());
            }
        }
    }

    public void ensureCollection() {
        String url = baseUrl() + "/collections/" + properties.getCollection();
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(12))
                .GET();
        addApiKey(builder);
        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 404) {
                createCollection();
                return;
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RagException("Qdrant collection check failed, HTTP " + response.statusCode() + ", body: " + response.body());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RagException("Qdrant collection check interrupted: " + ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new RagException("Qdrant collection check failed: " + ex.getMessage(), ex);
        }
    }

    public void upsert(String pointId, List<Double> vector, Map<String, Object> payload) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("id", pointId);
        point.put("vector", vector);
        point.put("payload", payload == null ? Map.of() : payload);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("points", List.of(point));
        putJson(baseUrl() + "/collections/" + properties.getCollection() + "/points?wait=true", body, "Qdrant upsert failed");
    }

    public List<VectorSearchHit> search(List<Double> queryVector, int limit) {
        int topK = Math.max(1, limit);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vector", queryVector);
        body.put("limit", topK);
        body.put("with_payload", true);
        body.put("with_vector", false);

        HttpResult searchResult = postJson(baseUrl() + "/collections/" + properties.getCollection() + "/points/search", body);
        if (searchResult.statusCode >= 200 && searchResult.statusCode < 300) {
            return parseSearchResult(searchResult.body);
        }

        Map<String, Object> fallbackBody = new LinkedHashMap<>();
        fallbackBody.put("query", queryVector);
        fallbackBody.put("limit", topK);
        fallbackBody.put("with_payload", true);
        fallbackBody.put("with_vector", false);
        HttpResult fallbackResult = postJson(baseUrl() + "/collections/" + properties.getCollection() + "/points/query", fallbackBody);
        if (fallbackResult.statusCode >= 200 && fallbackResult.statusCode < 300) {
            return parseSearchResult(fallbackResult.body);
        }

        throw new RagException("Qdrant search failed. search=" + searchResult.statusCode + ", query=" + fallbackResult.statusCode);
    }

    public void deletePoints(List<String> pointIds) {
        if (pointIds == null || pointIds.isEmpty()) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("points", pointIds);
        postJsonChecked(
                baseUrl() + "/collections/" + properties.getCollection() + "/points/delete?wait=true",
                body,
                "Qdrant delete failed"
        );
    }

    private void createCollection() {
        Map<String, Object> vectors = new LinkedHashMap<>();
        vectors.put("size", properties.getVectorDimension());
        vectors.put("distance", properties.getDistance());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vectors", vectors);
        putJson(baseUrl() + "/collections/" + properties.getCollection(), body, "Qdrant create collection failed");
    }

    private void putJson(String url, Map<String, Object> body, String errorPrefix) {
        try {
            String json = objectMapper.writeValueAsString(body);
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .PUT(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
            addApiKey(builder);
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RagException(errorPrefix + ", HTTP " + response.statusCode() + ", body: " + response.body());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RagException(errorPrefix + ", interrupted: " + ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new RagException(errorPrefix + ": " + ex.getMessage(), ex);
        }
    }

    private void postJsonChecked(String url, Map<String, Object> body, String errorPrefix) {
        HttpResult result = postJson(url, body);
        if (result.statusCode < 200 || result.statusCode >= 300) {
            throw new RagException(errorPrefix + ", HTTP " + result.statusCode + ", body: " + result.body);
        }
    }

    private HttpResult postJson(String url, Map<String, Object> body) {
        try {
            String json = objectMapper.writeValueAsString(body);
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
            addApiKey(builder);
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new HttpResult(response.statusCode(), response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RagException("Qdrant request interrupted: " + ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new RagException("Qdrant request failed: " + ex.getMessage(), ex);
        }
    }

    private List<VectorSearchHit> parseSearchResult(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode resultNode = root.path("result");
            JsonNode pointsNode = resultNode.isArray() ? resultNode : resultNode.path("points");
            List<VectorSearchHit> hits = new ArrayList<>();
            if (!pointsNode.isArray()) {
                return hits;
            }

            for (JsonNode point : pointsNode) {
                String pointId = point.path("id").asText();
                double score = point.path("score").asDouble(0.0);
                Map<String, Object> payload = objectMapper.convertValue(
                        point.path("payload"),
                        new TypeReference<Map<String, Object>>() {
                        }
                );
                hits.add(new VectorSearchHit(pointId, score, payload));
            }
            return hits;
        } catch (IOException ex) {
            throw new RagException("Failed to parse Qdrant search result: " + ex.getMessage(), ex);
        }
    }

    private void addApiKey(HttpRequest.Builder builder) {
        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            builder.header("api-key", properties.getApiKey().trim());
        }
    }

    private String baseUrl() {
        String url = properties.getUrl();
        if (url == null || url.isBlank()) {
            url = "http://localhost:6333";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private record HttpResult(int statusCode, String body) {
    }
}
