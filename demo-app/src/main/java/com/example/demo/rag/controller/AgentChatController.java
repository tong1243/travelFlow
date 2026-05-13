package com.example.demo.rag.controller;

import com.example.demo.rag.dto.AgentChatRequest;
import com.example.demo.rag.dto.AgentChatResponse;
import com.example.demo.rag.security.AuthenticatedUser;
import com.example.demo.rag.service.RagAgentService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/chat/agent")
/**
 * AgentChatController类。
 * 该类型负责接收并处理接口请求，协调服务层完成业务响应。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 */
public class AgentChatController {

    private final RagAgentService ragAgentService;
    private final ObjectMapper objectMapper;

    /**
     * 构造并初始化 AgentChatController 对象。
     * 该构造方法用于注入运行所需依赖，保证对象创建后即可参与完整流程。
     * 该方法位于控制层，负责参数承接、上下文透传和响应封装。
     * @param ragAgentService 输入参数 ragAgentService，用于参与本次处理流程。
     */
    public AgentChatController(RagAgentService ragAgentService, ObjectMapper objectMapper) {
        this.ragAgentService = ragAgentService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/ask")
    /**
     * 执行 ask 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于控制层，负责参数承接、上下文透传和响应封装。
     * @param user 输入参数 user，用于参与本次处理流程。
     * @param request 输入参数 request，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public AgentChatResponse ask(@AuthenticationPrincipal AuthenticatedUser user,
                                 @Valid @RequestBody AgentChatRequest request) {
        return ragAgentService.chat(user.getId(), isAdmin(user), request);
    }

    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(@AuthenticationPrincipal AuthenticatedUser user,
                                @Valid @RequestBody AgentChatRequest request) {
        SseEmitter emitter = new SseEmitter(0L);
        CompletableFuture.runAsync(() -> {
            try {
                AgentChatResponse response = ragAgentService.chatStream(
                        user.getId(),
                        isAdmin(user),
                        request,
                        chunk -> sendTextEvent(emitter, "delta", chunk),
                        status -> sendTextEvent(emitter, "status", status)
                );
                sendJsonEvent(emitter, "meta", response);
                sendTextEvent(emitter, "done", "[DONE]");
                emitter.complete();
            } catch (Exception ex) {
                sendTextEvent(emitter, "error", ex.getMessage() == null ? "流式输出失败。" : ex.getMessage());
                emitter.complete();
            }
        });
        return emitter;
    }

    private boolean isAdmin(AuthenticatedUser user) {
        return user != null && "ADMIN".equalsIgnoreCase(user.getRole());
    }

    private void sendTextEvent(SseEmitter emitter, String eventName, String data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data == null ? "" : data));
        } catch (Exception ignored) {
            // 客户端主动断开或网络抖动时，忽略发送异常，避免触发二次异常处理。
        }
    }

    private void sendJsonEvent(SseEmitter emitter, String eventName, Object payload) {
        try {
            String data = objectMapper.writeValueAsString(payload);
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("序列化流式事件失败。", ex);
        } catch (Exception ignored) {
            // 客户端主动断开时忽略发送异常。
        }
    }
}
