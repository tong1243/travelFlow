package com.example.demo.rag.dto;

import java.util.List;

/**
 * VectorSearchResponse记录类型。
 * 该类型负责封装请求与响应数据，保证接口契约清晰稳定。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 * @param items 记录字段 items，用于传递该对象的业务数据。
 */
public record VectorSearchResponse(
        List<VectorSearchItem> items
) {
}
