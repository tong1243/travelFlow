package com.example.demo.rag.service;

import com.example.demo.assistant.BailianClient;
import com.example.demo.assistant.BailianProperties;
import com.example.demo.rag.config.RagPipelineProperties;
import com.example.demo.rag.dto.ChatRequest;
import com.example.demo.rag.dto.ChatResponse;
import com.example.demo.rag.dto.RagReferenceItem;
import com.example.demo.rag.entity.ConversationSession;
import com.example.demo.rag.model.VectorSearchHit;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RagChatService {

    private static final String RAG_SYSTEM_PROMPT = """
            You are an AI travel assistant.
            - Give practical and safe travel guidance.
            - Prefer information from provided knowledge context.
            - If context is insufficient, explicitly say what is uncertain.
            - Keep answer structured with clear actionable suggestions.
            """;

    private final ConversationService conversationService;
    private final EmbeddingService embeddingService;
    private final QdrantVectorStoreClient vectorStoreClient;
    private final KnowledgeBaseService knowledgeBaseService;
    private final BailianClient bailianClient;
    private final BailianProperties bailianProperties;
    private final RagPipelineProperties ragPipelineProperties;

    public RagChatService(ConversationService conversationService,
                          EmbeddingService embeddingService,
                          QdrantVectorStoreClient vectorStoreClient,
                          KnowledgeBaseService knowledgeBaseService,
                          BailianClient bailianClient,
                          BailianProperties bailianProperties,
                          RagPipelineProperties ragPipelineProperties) {
        this.conversationService = conversationService;
        this.embeddingService = embeddingService;
        this.vectorStoreClient = vectorStoreClient;
        this.knowledgeBaseService = knowledgeBaseService;
        this.bailianClient = bailianClient;
        this.bailianProperties = bailianProperties;
        this.ragPipelineProperties = ragPipelineProperties;
    }

    public ChatResponse chat(Long userId, ChatRequest request) {
        ConversationSession session = conversationService.resolveSession(userId, request.sessionId(), request.question());
        conversationService.appendMessage(session.getId(), "user", request.question());

        int topK = request.topK() == null || request.topK() <= 0
                ? ragPipelineProperties.getTopK()
                : request.topK();
        List<Double> queryVector = embeddingService.vectorize(request.question());
        List<VectorSearchHit> hits = vectorStoreClient.search(queryVector, topK);
        List<RagReferenceItem> references = knowledgeBaseService.toReferenceItems(hits);

        List<ConversationService.ContextMessage> history = conversationService.getRecentContextMessages(session.getId(), 12);
        List<Map<String, String>> messages = buildModelMessages(history, references);
        String answer = bailianClient.chatWithMessages(bailianProperties.getDefaultModel(), messages);

        conversationService.appendMessage(session.getId(), "assistant", answer);
        return new ChatResponse(session.getId(), answer, bailianProperties.getDefaultModel(), references);
    }

    private List<Map<String, String>> buildModelMessages(List<ConversationService.ContextMessage> history,
                                                         List<RagReferenceItem> references) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(message("system", RAG_SYSTEM_PROMPT));

        String ragContext = buildRagContext(references);
        if (!ragContext.isBlank()) {
            messages.add(message("system", "Knowledge context:\n" + ragContext));
        }

        for (ConversationService.ContextMessage historyItem : history) {
            if ("assistant".equalsIgnoreCase(historyItem.role())) {
                messages.add(message("assistant", historyItem.content()));
            } else {
                messages.add(message("user", historyItem.content()));
            }
        }
        return messages;
    }

    private String buildRagContext(List<RagReferenceItem> references) {
        if (references == null || references.isEmpty()) {
            return "";
        }
        int maxChars = Math.max(1200, ragPipelineProperties.getContextMaxChars());
        StringBuilder builder = new StringBuilder();
        int index = 1;
        for (RagReferenceItem item : references) {
            String line = """
                    [%d] %s (score=%.4f)
                    %s
                    
                    """.formatted(index++, item.documentTitle(), item.score(), item.snippet());
            if (builder.length() + line.length() > maxChars) {
                break;
            }
            builder.append(line);
        }
        return builder.toString().trim();
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("role", role);
        item.put("content", content);
        return item;
    }
}
