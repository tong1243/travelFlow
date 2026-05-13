package com.example.demo.rag.dto;

/**
 * AgentToolTrace记录类型。
 * 该类型负责封装请求与响应数据，保证接口契约清晰稳定。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 * @param step 记录字段 step，用于传递该对象的业务数据。
 * @param toolName 记录字段 toolName，用于传递该对象的业务数据。
 * @param toolInput 记录字段 toolInput，用于传递该对象的业务数据。
 * @param toolOutputSummary 记录字段 toolOutputSummary，用于传递该对象的业务数据。
 */
public record AgentToolTrace(
        int step,
        String toolName,
        String toolInput,
        String toolOutputSummary
) {
}
