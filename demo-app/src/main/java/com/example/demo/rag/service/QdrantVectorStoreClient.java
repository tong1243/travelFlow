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
/**
 * QdrantVectorStoreClient类。
 * 该类型负责组织核心业务流程，串联检索、存储与模型调用能力。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 */
public class QdrantVectorStoreClient {

    private static final Logger log = LoggerFactory.getLogger(QdrantVectorStoreClient.class);

    private final VectorDbProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    /**
     * 构造并初始化 QdrantVectorStoreClient 对象。
     * 该构造方法用于注入运行所需依赖，保证对象创建后即可参与完整流程。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param properties 输入参数 properties，用于参与本次处理流程。
     * @param objectMapper 输入参数 objectMapper，用于参与本次处理流程。
     */
    public QdrantVectorStoreClient(VectorDbProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    @PostConstruct
    /**
     * 执行 initCollection 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     */
    public void initCollection() {
        if (properties.isCreateIfMissing()) {
            try {
                ensureCollection();
            } catch (RagException ex) {
                log.warn("启动阶段无法连接向量库，检索增强向量能力将在服务恢复后可用。{}", ex.getMessage());
            }
        }
    }

    /**
     * 执行 ensureCollection 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     */
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
                throw new RagException("向量库集合检查失败，状态码 " + response.statusCode() + "，响应：" + response.body());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RagException("向量库集合检查被中断：" + ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new RagException("向量库集合检查失败：" + ex.getMessage(), ex);
        }
    }

    /**
     * 执行 upsert 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param pointId 输入参数 pointId，用于参与本次处理流程。
     * @param vector 输入参数 vector，用于参与本次处理流程。
     * @param payload 输入参数 payload，用于参与本次处理流程。
     */
    public void upsert(String pointId, List<Double> vector, Map<String, Object> payload) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("id", pointId);
        point.put("vector", vector);
        point.put("payload", payload == null ? Map.of() : payload);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("points", List.of(point));
        putJson(baseUrl() + "/collections/" + properties.getCollection() + "/points?wait=true", body, "向量库写入失败");
    }

    /**
     * 执行 search 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param queryVector 输入参数 queryVector，用于参与本次处理流程。
     * @param limit 输入参数 limit，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
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

        throw new RagException("向量库检索失败。主检索状态码=" + searchResult.statusCode + "，回退检索状态码=" + fallbackResult.statusCode);
    }

    /**
     * 执行 deletePoints 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param pointIds 输入参数 pointIds，用于参与本次处理流程。
     */
    public void deletePoints(List<String> pointIds) {
        if (pointIds == null || pointIds.isEmpty()) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("points", pointIds);
        postJsonChecked(
                baseUrl() + "/collections/" + properties.getCollection() + "/points/delete?wait=true",
                body,
                "向量库删除失败"
        );
    }

    /**
     * 执行 createCollection 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     */
    private void createCollection() {
        Map<String, Object> vectors = new LinkedHashMap<>();
        vectors.put("size", properties.getVectorDimension());
        vectors.put("distance", properties.getDistance());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vectors", vectors);
        putJson(baseUrl() + "/collections/" + properties.getCollection(), body, "向量库创建集合失败");
    }

    /**
     * 执行 putJson 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param url 输入参数 url，用于参与本次处理流程。
     * @param body 输入参数 body，用于参与本次处理流程。
     * @param errorPrefix 输入参数 errorPrefix，用于参与本次处理流程。
     */
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
                throw new RagException(errorPrefix + "，状态码 " + response.statusCode() + "，响应：" + response.body());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RagException(errorPrefix + "，请求被中断：" + ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new RagException(errorPrefix + "：" + ex.getMessage(), ex);
        }
    }

    /**
     * 执行 postJsonChecked 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param url 输入参数 url，用于参与本次处理流程。
     * @param body 输入参数 body，用于参与本次处理流程。
     * @param errorPrefix 输入参数 errorPrefix，用于参与本次处理流程。
     */
    private void postJsonChecked(String url, Map<String, Object> body, String errorPrefix) {
        HttpResult result = postJson(url, body);
        if (result.statusCode < 200 || result.statusCode >= 300) {
            throw new RagException(errorPrefix + "，状态码 " + result.statusCode + "，响应：" + result.body);
        }
    }

    /**
     * 执行 postJson 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param url 输入参数 url，用于参与本次处理流程。
     * @param body 输入参数 body，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
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
            throw new RagException("向量库请求被中断：" + ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new RagException("向量库请求失败：" + ex.getMessage(), ex);
        }
    }

    /**
     * 执行 parseSearchResult 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param responseBody 输入参数 responseBody，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
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
            throw new RagException("解析向量库检索结果失败：" + ex.getMessage(), ex);
        }
    }

    /**
     * 执行 addApiKey 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param builder 输入参数 builder，用于参与本次处理流程。
     */
    private void addApiKey(HttpRequest.Builder builder) {
        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            builder.header("api-key", properties.getApiKey().trim());
        }
    }

    /**
     * 执行 baseUrl 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    private String baseUrl() {
        String url = properties.getUrl();
        if (url == null || url.isBlank()) {
            url = "http://localhost:6333";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * HttpResult记录类型。
     * 该类型负责组织核心业务流程，串联检索、存储与模型调用能力。
     * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
     * @param statusCode 记录字段 statusCode，用于传递该对象的业务数据。
     * @param body 记录字段 body，用于传递该对象的业务数据。
     */
    private record HttpResult(int statusCode, String body) {
    }
}
