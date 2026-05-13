package com.example.demo.rag.service;

import com.example.demo.rag.RagException;
import com.example.demo.rag.dto.ConversationMessageResponse;
import com.example.demo.rag.dto.ConversationSummaryResponse;
import com.example.demo.rag.entity.ConversationMessage;
import com.example.demo.rag.entity.ConversationSession;
import com.example.demo.rag.repo.ConversationMessageRepository;
import com.example.demo.rag.repo.ConversationSessionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
/**
 * ConversationService类。
 * 该类型负责组织核心业务流程，串联检索、存储与模型调用能力。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 */
public class ConversationService {

    private static final String SESSION_CACHE_KEY_PREFIX = "rag:session:";
    private static final int CACHE_MAX_MESSAGES = 40;

    private final ConversationSessionRepository sessionRepository;
    private final ConversationMessageRepository messageRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 构造并初始化 ConversationService 对象。
     * 该构造方法用于注入运行所需依赖，保证对象创建后即可参与完整流程。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param sessionRepository 输入参数 sessionRepository，用于参与本次处理流程。
     * @param messageRepository 输入参数 messageRepository，用于参与本次处理流程。
     * @param redisTemplate 输入参数 redisTemplate，用于参与本次处理流程。
     * @param objectMapper 输入参数 objectMapper，用于参与本次处理流程。
     */
    public ConversationService(ConversationSessionRepository sessionRepository,
                               ConversationMessageRepository messageRepository,
                               StringRedisTemplate redisTemplate,
                               ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    /**
     * 执行 resolveSession 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param userId 输入参数 userId，用于参与本次处理流程。
     * @param sessionId 输入参数 sessionId，用于参与本次处理流程。
     * @param firstQuestion 输入参数 firstQuestion，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public ConversationSession resolveSession(Long userId, String sessionId, String firstQuestion) {
        if (sessionId == null || sessionId.isBlank()) {
            ConversationSession session = new ConversationSession();
            session.setId(UUID.randomUUID().toString().replace("-", ""));
            session.setUserId(userId);
            session.setTitle(buildTitle(firstQuestion));
            return sessionRepository.save(session);
        }
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new RagException("会话不存在或无访问权限。"));
    }

    @Transactional
    /**
     * 执行 appendMessage 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param sessionId 输入参数 sessionId，用于参与本次处理流程。
     * @param role 输入参数 role，用于参与本次处理流程。
     * @param content 输入参数 content，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public ConversationMessage appendMessage(String sessionId, String role, String content) {
        ConversationMessage message = new ConversationMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        ConversationMessage saved = messageRepository.save(message);
        sessionRepository.touchSession(sessionId, Instant.now());
        appendCache(saved);
        return saved;
    }

    /**
     * 执行 listSessionHistory 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param userId 输入参数 userId，用于参与本次处理流程。
     * @param sessionId 输入参数 sessionId，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public List<ConversationMessageResponse> listSessionHistory(Long userId, String sessionId) {
        sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new RagException("会话不存在或无访问权限。"));
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId).stream()
                .map(item -> new ConversationMessageResponse(item.getId(), item.getRole(), item.getContent(), item.getCreatedAt()))
                .toList();
    }

    /**
     * 执行 listSessions 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param userId 输入参数 userId，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public List<ConversationSummaryResponse> listSessions(Long userId) {
        return sessionRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(item -> new ConversationSummaryResponse(item.getId(), item.getTitle(), item.getUpdatedAt()))
                .toList();
    }

    /**
     * 获取 RecentContextMessages 字段值。
     * 该方法提供只读访问入口，减少调用方直接操作内部状态的风险。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param sessionId 输入参数 sessionId，用于参与本次处理流程。
     * @param limit 输入参数 limit，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public List<ContextMessage> getRecentContextMessages(String sessionId, int limit) {
        int normalizedLimit = Math.max(1, Math.min(limit, CACHE_MAX_MESSAGES));
        List<ContextMessage> cached = readFromCache(sessionId, normalizedLimit);
        if (cached.size() >= normalizedLimit) {
            return cached;
        }

        List<ConversationMessage> dbMessages = new ArrayList<>(messageRepository.findTop20BySessionIdOrderByCreatedAtDesc(sessionId));
        Collections.reverse(dbMessages);
        if (dbMessages.size() > normalizedLimit) {
            dbMessages = dbMessages.subList(dbMessages.size() - normalizedLimit, dbMessages.size());
        }
        refreshCache(sessionId, dbMessages);
        return dbMessages.stream()
                .map(item -> new ContextMessage(item.getRole(), item.getContent()))
                .toList();
    }

    /**
     * 执行 buildTitle 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param question 输入参数 question，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    private String buildTitle(String question) {
        if (question == null || question.isBlank()) {
            return "新行程";
        }
        String trimmed = question.trim();
        return trimmed.length() > 24 ? trimmed.substring(0, 24) + "..." : trimmed;
    }

    /**
     * 执行 appendCache 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param message 输入参数 message，用于参与本次处理流程。
     */
    private void appendCache(ConversationMessage message) {
        String key = cacheKey(message.getSessionId());
        try {
            String json = objectMapper.writeValueAsString(new CachedMessage(message.getRole(), message.getContent(), message.getCreatedAt()));
            redisTemplate.opsForList().rightPush(key, json);
            redisTemplate.opsForList().trim(key, -CACHE_MAX_MESSAGES, -1);
            redisTemplate.expire(key, Duration.ofDays(7));
        } catch (JsonProcessingException ex) {
            // 缓存序列化失败时忽略，数据库仍是最终数据源。
        }
    }

    /**
     * 执行 readFromCache 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param sessionId 输入参数 sessionId，用于参与本次处理流程。
     * @param limit 输入参数 limit，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    private List<ContextMessage> readFromCache(String sessionId, int limit) {
        String key = cacheKey(sessionId);
        List<String> raw = redisTemplate.opsForList().range(key, -limit, -1);
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }

        List<CachedMessage> cached = new ArrayList<>();
        for (String item : raw) {
            try {
                cached.add(objectMapper.readValue(item, CachedMessage.class));
            } catch (JsonProcessingException ignored) {
                // 缓存项格式异常时忽略，避免影响主流程。
            }
        }
        cached.sort(Comparator.comparing(CachedMessage::createdAt));
        return cached.stream()
                .map(item -> new ContextMessage(item.role(), item.content()))
                .toList();
    }

    /**
     * 执行 refreshCache 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param sessionId 输入参数 sessionId，用于参与本次处理流程。
     * @param dbMessages 输入参数 dbMessages，用于参与本次处理流程。
     */
    private void refreshCache(String sessionId, List<ConversationMessage> dbMessages) {
        String key = cacheKey(sessionId);
        redisTemplate.delete(key);
        if (dbMessages.isEmpty()) {
            return;
        }
        for (ConversationMessage item : dbMessages) {
            appendCache(item);
        }
    }

    /**
     * 执行 cacheKey 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param sessionId 输入参数 sessionId，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    private String cacheKey(String sessionId) {
        return SESSION_CACHE_KEY_PREFIX + sessionId + ":messages";
    }

    /**
     * ContextMessage记录类型。
     * 该类型负责组织核心业务流程，串联检索、存储与模型调用能力。
     * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
     * @param role 记录字段 role，用于传递该对象的业务数据。
     * @param content 记录字段 content，用于传递该对象的业务数据。
     */
    public record ContextMessage(String role, String content) {
    }

    /**
     * CachedMessage记录类型。
     * 该类型负责组织核心业务流程，串联检索、存储与模型调用能力。
     * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
     * @param role 记录字段 role，用于传递该对象的业务数据。
     * @param content 记录字段 content，用于传递该对象的业务数据。
     * @param createdAt 记录字段 createdAt，用于传递该对象的业务数据。
     */
    private record CachedMessage(String role, String content, Instant createdAt) {
    }
}
