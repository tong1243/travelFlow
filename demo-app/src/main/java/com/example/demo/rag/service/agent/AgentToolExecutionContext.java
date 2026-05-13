package com.example.demo.rag.service.agent;

import com.example.demo.rag.dto.AgentChatRequest;

/**
 * AgentToolExecutionContext 记录类型。
 * 用于承载一次调用过程中的结构化数据，减少样板代码并提升可读性。
 * 该类型通常作为方法返回值或中间上下文，在工具间安全传递。
 */
public record AgentToolExecutionContext(AgentToolRuntime runtime,
                                        AgentChatRequest request,
                                        Long userId,
                                        boolean isAdmin,
                                        boolean allowHighRiskTools) {
}