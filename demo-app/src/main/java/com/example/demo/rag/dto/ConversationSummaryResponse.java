package com.example.demo.rag.dto;

import java.time.Instant;

/**
 * ConversationSummaryResponse记录类型。
 * 该类型负责封装请求与响应数据，保证接口契约清晰稳定。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 * @param sessionId 记录字段 sessionId，用于传递该对象的业务数据。
 * @param title 记录字段 title，用于传递该对象的业务数据。
 * @param updatedAt 记录字段 updatedAt，用于传递该对象的业务数据。
 */
public record ConversationSummaryResponse(
        String sessionId,
        String title,
        Instant updatedAt
) {
}
