package com.example.demo.rag.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * VectorSearchRequest记录类型。
 * 该类型负责封装请求与响应数据，保证接口契约清晰稳定。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 * @param query 记录字段 query，用于传递该对象的业务数据。
 * @param topK 记录字段 topK，用于传递该对象的业务数据。
 */
public record VectorSearchRequest(
        @NotBlank String query,
        Integer topK
) {
}
