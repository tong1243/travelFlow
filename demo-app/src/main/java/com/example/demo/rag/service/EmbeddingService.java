package com.example.demo.rag.service;

import com.example.demo.assistant.BailianClient;
import com.example.demo.rag.RagException;
import com.example.demo.rag.config.VectorDbProperties;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
/**
 * EmbeddingService类。
 * 该类型负责组织核心业务流程，串联检索、存储与模型调用能力。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 */
public class EmbeddingService {

    private final BailianClient bailianClient;
    private final VectorDbProperties vectorDbProperties;

    /**
     * 构造并初始化 EmbeddingService 对象。
     * 该构造方法用于注入运行所需依赖，保证对象创建后即可参与完整流程。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param bailianClient 输入参数 bailianClient，用于参与本次处理流程。
     * @param vectorDbProperties 输入参数 vectorDbProperties，用于参与本次处理流程。
     */
    public EmbeddingService(BailianClient bailianClient, VectorDbProperties vectorDbProperties) {
        this.bailianClient = bailianClient;
        this.vectorDbProperties = vectorDbProperties;
    }

    /**
     * 执行 vectorize 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param text 输入参数 text，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public List<Double> vectorize(String text) {
        List<Double> vector = bailianClient.embed(text);
        int expected = vectorDbProperties.getVectorDimension();
        if (expected > 0 && vector.size() != expected) {
            throw new RagException("向量维度不匹配，期望=" + expected + "，实际=" + vector.size());
        }
        return vector;
    }
}
