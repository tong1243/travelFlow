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
/**
 * VectorService类。
 * 该类型负责组织核心业务流程，串联检索、存储与模型调用能力。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 */
public class VectorService {

    private final EmbeddingService embeddingService;
    private final QdrantVectorStoreClient vectorStoreClient;

    /**
     * 构造并初始化 VectorService 对象。
     * 该构造方法用于注入运行所需依赖，保证对象创建后即可参与完整流程。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param embeddingService 输入参数 embeddingService，用于参与本次处理流程。
     * @param vectorStoreClient 输入参数 vectorStoreClient，用于参与本次处理流程。
     */
    public VectorService(EmbeddingService embeddingService, QdrantVectorStoreClient vectorStoreClient) {
        this.embeddingService = embeddingService;
        this.vectorStoreClient = vectorStoreClient;
    }

    /**
     * 执行 vectorize 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param text 输入参数 text，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public VectorizeResponse vectorize(String text) {
        List<Double> vector = embeddingService.vectorize(text);
        return new VectorizeResponse(vector.size(), vector);
    }

    /**
     * 执行 search 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param request 输入参数 request，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public VectorSearchResponse search(VectorSearchRequest request) {
        int topK = request.topK() == null || request.topK() <= 0 ? 5 : request.topK();
        List<Double> queryVector = embeddingService.vectorize(request.query());
        List<VectorSearchHit> hits = vectorStoreClient.search(queryVector, topK);
        List<VectorSearchItem> items = hits.stream()
                .map(item -> new VectorSearchItem(item.pointId(), item.score(), item.payload()))
                .toList();
        return new VectorSearchResponse(items);
    }

    /**
     * 执行 upsert 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param request 输入参数 request，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
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
