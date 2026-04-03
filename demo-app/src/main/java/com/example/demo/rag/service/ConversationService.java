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
public class ConversationService {

    private static final String SESSION_CACHE_KEY_PREFIX = "rag:session:";
    private static final int CACHE_MAX_MESSAGES = 40;

    private final ConversationSessionRepository sessionRepository;
    private final ConversationMessageRepository messageRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

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
    public ConversationSession resolveSession(Long userId, String sessionId, String firstQuestion) {
        if (sessionId == null || sessionId.isBlank()) {
            ConversationSession session = new ConversationSession();
            session.setId(UUID.randomUUID().toString().replace("-", ""));
            session.setUserId(userId);
            session.setTitle(buildTitle(firstQuestion));
            return sessionRepository.save(session);
        }
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new RagException("Conversation session not found or no permission."));
    }

    @Transactional
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

    public List<ConversationMessageResponse> listSessionHistory(Long userId, String sessionId) {
        sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new RagException("Conversation session not found or no permission."));
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId).stream()
                .map(item -> new ConversationMessageResponse(item.getId(), item.getRole(), item.getContent(), item.getCreatedAt()))
                .toList();
    }

    public List<ConversationSummaryResponse> listSessions(Long userId) {
        return sessionRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(item -> new ConversationSummaryResponse(item.getId(), item.getTitle(), item.getUpdatedAt()))
                .toList();
    }

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

    private String buildTitle(String question) {
        if (question == null || question.isBlank()) {
            return "New Trip";
        }
        String trimmed = question.trim();
        return trimmed.length() > 24 ? trimmed.substring(0, 24) + "..." : trimmed;
    }

    private void appendCache(ConversationMessage message) {
        String key = cacheKey(message.getSessionId());
        try {
            String json = objectMapper.writeValueAsString(new CachedMessage(message.getRole(), message.getContent(), message.getCreatedAt()));
            redisTemplate.opsForList().rightPush(key, json);
            redisTemplate.opsForList().trim(key, -CACHE_MAX_MESSAGES, -1);
            redisTemplate.expire(key, Duration.ofDays(7));
        } catch (JsonProcessingException ex) {
            // Ignore cache serialization errors; DB is still source of truth.
        }
    }

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
                // Ignore malformed cached item.
            }
        }
        cached.sort(Comparator.comparing(CachedMessage::createdAt));
        return cached.stream()
                .map(item -> new ContextMessage(item.role(), item.content()))
                .toList();
    }

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

    private String cacheKey(String sessionId) {
        return SESSION_CACHE_KEY_PREFIX + sessionId + ":messages";
    }

    public record ContextMessage(String role, String content) {
    }

    private record CachedMessage(String role, String content, Instant createdAt) {
    }
}
