package com.example.demo.rag.service;

import com.example.demo.rag.config.RagPipelineProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
/**
 * TextChunkingService类。
 * 该类型负责组织核心业务流程，串联检索、存储与模型调用能力。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 */
public class TextChunkingService {

    private final RagPipelineProperties properties;

    /**
     * 构造并初始化 TextChunkingService 对象。
     * 该构造方法用于注入运行所需依赖，保证对象创建后即可参与完整流程。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param properties 输入参数 properties，用于参与本次处理流程。
     */
    public TextChunkingService(RagPipelineProperties properties) {
        this.properties = properties;
    }

    /**
     * 执行 splitToChunks 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param content 输入参数 content，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
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
