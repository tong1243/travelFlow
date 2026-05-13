package com.example.demo.rag.dto;

import java.time.Instant;

/**
 * KnowledgeDocumentResponse记录类型。
 * 该类型负责封装请求与响应数据，保证接口契约清晰稳定。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 * @param documentId 记录字段 documentId，用于传递该对象的业务数据。
 * @param title 记录字段 title，用于传递该对象的业务数据。
 * @param sourceType 记录字段 sourceType，用于传递该对象的业务数据。
 * @param sourceRef 记录字段 sourceRef，用于传递该对象的业务数据。
 * @param status 记录字段 status，用于传递该对象的业务数据。
 * @param versionNo 记录字段 versionNo，用于传递该对象的业务数据。
 * @param chunkCount 记录字段 chunkCount，用于传递该对象的业务数据。
 * @param updatedAt 记录字段 updatedAt，用于传递该对象的业务数据。
 */
public record KnowledgeDocumentResponse(
        Long documentId,
        String title,
        String sourceType,
        String sourceRef,
        String status,
        int versionNo,
        int chunkCount,
        Instant updatedAt
) {
}
