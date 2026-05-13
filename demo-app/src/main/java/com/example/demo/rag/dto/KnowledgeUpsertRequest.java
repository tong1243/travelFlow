package com.example.demo.rag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * KnowledgeUpsertRequest记录类型。
 * 该类型负责封装请求与响应数据，保证接口契约清晰稳定。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 * @param title 记录字段 title，用于传递该对象的业务数据。
 * @param content 记录字段 content，用于传递该对象的业务数据。
 * @param sourceType 记录字段 sourceType，用于传递该对象的业务数据。
 * @param sourceRef 记录字段 sourceRef，用于传递该对象的业务数据。
 */
public record KnowledgeUpsertRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank String content,
        @Size(max = 64) String sourceType,
        @Size(max = 255) String sourceRef
) {
}
