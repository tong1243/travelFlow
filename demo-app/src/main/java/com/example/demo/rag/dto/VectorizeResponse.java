package com.example.demo.rag.dto;

import java.util.List;

/**
 * VectorizeResponse记录类型。
 * 该类型负责封装请求与响应数据，保证接口契约清晰稳定。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 * @param dimension 记录字段 dimension，用于传递该对象的业务数据。
 * @param vector 记录字段 vector，用于传递该对象的业务数据。
 */
public record VectorizeResponse(
        int dimension,
        List<Double> vector
) {
}
