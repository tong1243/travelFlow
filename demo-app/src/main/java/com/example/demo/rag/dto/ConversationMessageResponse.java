package com.example.demo.rag.dto;

import java.time.Instant;

/**
 * ConversationMessageResponse记录类型。
 * 该类型负责封装请求与响应数据，保证接口契约清晰稳定。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 * @param id 记录字段 id，用于传递该对象的业务数据。
 * @param role 记录字段 role，用于传递该对象的业务数据。
 * @param content 记录字段 content，用于传递该对象的业务数据。
 * @param createdAt 记录字段 createdAt，用于传递该对象的业务数据。
 */
public record ConversationMessageResponse(
        Long id,
        String role,
        String content,
        Instant createdAt
) {
}
