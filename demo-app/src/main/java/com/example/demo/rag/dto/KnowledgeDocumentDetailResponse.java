package com.example.demo.rag.dto;

import java.time.Instant;

/**
 * KnowledgeDocumentDetailResponse记录类型。
 * 该类型用于返回单条知识文档详情，支持前端执行“编辑偏好”回填。
 *
 * @param documentId 文档ID
 * @param title 文档标题
 * @param content 文档正文内容
 * @param sourceType 来源类型
 * @param sourceRef 来源标识
 * @param status 文档状态
 * @param versionNo 版本号
 * @param chunkCount 切片数
 * @param updatedAt 更新时间
 */
public record KnowledgeDocumentDetailResponse(
        Long documentId,
        String title,
        String content,
        String sourceType,
        String sourceRef,
        String status,
        int versionNo,
        int chunkCount,
        Instant updatedAt
) {
}
