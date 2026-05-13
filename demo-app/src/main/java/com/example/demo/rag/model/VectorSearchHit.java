package com.example.demo.rag.model;

import java.util.Map;

/**
 * VectorSearchHit记录类型。
 * 该类型负责承载检索阶段的中间结果，便于融合排序与结果转换。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 * @param pointId 记录字段 pointId，用于传递该对象的业务数据。
 * @param score 记录字段 score，用于传递该对象的业务数据。
 * @param payload 记录字段 payload，用于传递该对象的业务数据。
 */
public record VectorSearchHit(
        String pointId,
        double score,
        Map<String, Object> payload
) {
}
