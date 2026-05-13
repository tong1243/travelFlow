package com.example.demo.rag.dto;

import java.util.List;

/**
 * AgentChatResponse记录类型。
 * 该类型负责封装请求与响应数据，保证接口契约清晰稳定。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 * @param sessionId 记录字段 sessionId，用于传递该对象的业务数据。
 * @param answer 记录字段 answer，用于传递该对象的业务数据。
 * @param model 记录字段 model，用于传递该对象的业务数据。
 * @param references 记录字段 references，用于传递该对象的业务数据。
 * @param traces 记录字段 traces，用于传递该对象的业务数据。
 * @param currentMode 记录字段 currentMode，用于传递当前智能体执行模式。
 * @param executedTools 记录字段 executedTools，用于传递本轮实际执行过的工具名列表。
 * @param blockedTools 记录字段 blockedTools，用于传递被高风险闸门拦截的工具名列表。
 * @param gateReason 记录字段 gateReason，用于传递本轮高风险闸门判定原因。
 * @param planScoreSummary 记录字段 planScoreSummary，用于传递本轮方案评分摘要，便于前端渲染对比卡片。
 */
public record AgentChatResponse(
        String sessionId,
        String answer,
        String model,
        List<RagReferenceItem> references,
        List<AgentToolTrace> traces,
        String currentMode,
        List<String> executedTools,
        List<String> blockedTools,
        String gateReason,
        String planScoreSummary
) {
}
