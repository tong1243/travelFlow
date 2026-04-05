package com.example.demo.rag.service;

import com.example.demo.assistant.BailianClient;
import com.example.demo.assistant.BailianProperties;
import com.example.demo.rag.config.RagPipelineProperties;
import com.example.demo.rag.dto.ChatRequest;
import com.example.demo.rag.dto.ChatResponse;
import com.example.demo.rag.dto.RagReferenceItem;
import com.example.demo.rag.entity.ConversationSession;
import com.example.demo.rag.langchain.RagLangChainComposer;
import com.example.demo.rag.model.HybridSearchHit;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class RagChatService {

    private final ConversationService conversationService;
    private final HybridRetrievalService hybridRetrievalService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final BailianClient bailianClient;
    private final BailianProperties bailianProperties;
    private final RagPipelineProperties ragPipelineProperties;
    private final RagLangChainComposer ragLangChainComposer;

    public RagChatService(ConversationService conversationService,
                          HybridRetrievalService hybridRetrievalService,
                          KnowledgeBaseService knowledgeBaseService,
                          BailianClient bailianClient,
                          BailianProperties bailianProperties,
                          RagPipelineProperties ragPipelineProperties,
                          RagLangChainComposer ragLangChainComposer) {
        this.conversationService = conversationService;
        this.hybridRetrievalService = hybridRetrievalService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.bailianClient = bailianClient;
        this.bailianProperties = bailianProperties;
        this.ragPipelineProperties = ragPipelineProperties;
        this.ragLangChainComposer = ragLangChainComposer;
    }

    public ChatResponse chat(Long userId, ChatRequest request) {
        ConversationSession session = conversationService.resolveSession(userId, request.sessionId(), request.question());
        conversationService.appendMessage(session.getId(), "user", request.question());

        int topK = request.topK() == null || request.topK() <= 0
                ? ragPipelineProperties.getTopK()
                : request.topK();
        List<HybridSearchHit> hits = hybridRetrievalService.retrieve(
                request.question(),
                topK,
                request.sourceType(),
                request.sourceRefContains()
        );
        List<RagReferenceItem> references = knowledgeBaseService.toHybridReferenceItems(hits);

        List<ConversationService.ContextMessage> history = conversationService.getRecentContextMessages(session.getId(), 12);
        String ragContext = buildRagContext(references);
        List<Map<String, String>> messages = ragLangChainComposer.composeMessages(history, references, ragContext);
        String answer = bailianClient.chatWithMessages(bailianProperties.getDefaultModel(), messages);

        conversationService.appendMessage(session.getId(), "assistant", answer);
        return new ChatResponse(session.getId(), answer, bailianProperties.getDefaultModel(), references);
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
                    [%d] %s | 来源类型=%s | 来源标识=%s
                    综合分=%.4f, 向量分=%.4f, 词法分=%.4f, 重排分=%.4f
                    %s
                    
                    """.formatted(
                    index++,
                    item.documentTitle(),
                    fallback(item.sourceType(), "未知"),
                    fallback(item.sourceRef(), "未知"),
                    item.score(),
                    item.vectorScore(),
                    item.lexicalScore(),
                    item.rerankScore(),
                    item.snippet()
            );
            if (builder.length() + line.length() > maxChars) {
                break;
            }
            builder.append(line);
        }
        return builder.toString().trim();
    }

    private String fallback(String text, String defaultValue) {
        return text == null || text.isBlank() ? defaultValue : text;
    }
}

