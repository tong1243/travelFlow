package com.example.demo.rag.service;

import com.example.demo.assistant.BailianClient;
import com.example.demo.assistant.BailianProperties;
import com.example.demo.rag.config.RagPipelineProperties;
import com.example.demo.rag.dto.AgentChatRequest;
import com.example.demo.rag.dto.AgentChatResponse;
import com.example.demo.rag.dto.AgentToolTrace;
import com.example.demo.rag.dto.RagReferenceItem;
import com.example.demo.rag.entity.ConversationSession;
import com.example.demo.rag.langchain.RagLangChainComposer;
import com.example.demo.rag.model.HybridSearchHit;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RagAgentService {

    private static final Pattern DAY_PATTERN = Pattern.compile("(\\d{1,2})\\s*(天|day|days)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRAVELER_PATTERN = Pattern.compile("(\\d{1,2})\\s*(人|位|traveler|travelers|people)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BUDGET_PATTERN = Pattern.compile("(\\d{3,8})\\s*(元|rmb|cny|¥|￥)?", Pattern.CASE_INSENSITIVE);

    private final ConversationService conversationService;
    private final HybridRetrievalService hybridRetrievalService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final BailianClient bailianClient;
    private final BailianProperties bailianProperties;
    private final RagPipelineProperties ragPipelineProperties;
    private final RagLangChainComposer ragLangChainComposer;

    public RagAgentService(ConversationService conversationService,
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

    public AgentChatResponse chat(Long userId, AgentChatRequest request) {
        ConversationSession session = conversationService.resolveSession(userId, request.sessionId(), request.question());
        conversationService.appendMessage(session.getId(), "user", request.question());

        int stepLimit = Math.max(1, ragPipelineProperties.getAgentMaxSteps());
        int topK = request.topK() == null || request.topK() <= 0 ? ragPipelineProperties.getTopK() : request.topK();
        List<AgentToolTrace> traces = new ArrayList<>();

        int stepCounter = 1;
        List<HybridSearchHit> hits = hybridRetrievalService.retrieve(
                request.question(),
                topK,
                request.sourceType(),
                request.sourceRefContains()
        );
        List<RagReferenceItem> references = knowledgeBaseService.toHybridReferenceItems(hits);
        traces.add(new AgentToolTrace(
                stepCounter++,
                "knowledge_search",
                "topK=" + topK + ", sourceType=" + fallback(request.sourceType(), "ANY") + ", sourceRefContains=" + fallback(request.sourceRefContains(), "ANY"),
                "Retrieved " + references.size() + " references."
        ));

        String budgetSummary = "";
        if (stepCounter <= stepLimit && shouldRunBudgetEstimator(request.question())) {
            budgetSummary = estimateBudgetFromQuestion(request.question());
            traces.add(new AgentToolTrace(
                    stepCounter++,
                    "budget_estimator",
                    request.question(),
                    budgetSummary
            ));
        }

        List<ConversationService.ContextMessage> history = conversationService.getRecentContextMessages(session.getId(), 12);
        String ragContext = buildRagContext(references);
        List<Map<String, String>> messages = ragLangChainComposer.composeMessages(history, references, ragContext);
        messages.add(1, message("system", buildAgentInstruction(stepLimit, traces, budgetSummary)));

        String answer = bailianClient.chatWithMessages(bailianProperties.getDefaultModel(), messages);
        conversationService.appendMessage(session.getId(), "assistant", answer);

        List<AgentToolTrace> outputTraces = Boolean.FALSE.equals(request.includeTrace()) ? List.of() : traces;
        return new AgentChatResponse(session.getId(), answer, bailianProperties.getDefaultModel(), references, outputTraces);
    }

    private String buildAgentInstruction(int stepLimit, List<AgentToolTrace> traces, String budgetSummary) {
        StringBuilder builder = new StringBuilder();
        builder.append("Agent execution mode is enabled.\n");
        builder.append("- Step limit: ").append(stepLimit).append('\n');
        builder.append("- Tools already executed in this turn:\n");
        for (AgentToolTrace trace : traces) {
            builder.append("  [Step ").append(trace.step()).append("] ").append(trace.toolName()).append('\n');
            builder.append("  input: ").append(trace.toolInput()).append('\n');
            builder.append("  output: ").append(trace.toolOutputSummary()).append('\n');
        }
        if (!budgetSummary.isBlank()) {
            builder.append("- Budget estimator result:\n").append(budgetSummary).append('\n');
        }
        builder.append("- In final answer, include: plan, cost notes, risk tips, and cite references like [1][2].");
        return builder.toString();
    }

    private boolean shouldRunBudgetEstimator(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        String lower = question.toLowerCase();
        return lower.contains("预算")
                || lower.contains("花费")
                || lower.contains("费用")
                || lower.contains("cost")
                || lower.contains("budget");
    }

    private String estimateBudgetFromQuestion(String question) {
        int days = extractInt(DAY_PATTERN, question, 3, 1, 30);
        int travelers = extractInt(TRAVELER_PATTERN, question, 1, 1, 20);
        int userBudget = extractInt(BUDGET_PATTERN, question, -1, -1, Integer.MAX_VALUE);

        // Fast deterministic estimate to keep agent tool behavior stable and explainable.
        int low = days * travelers * 320;
        int medium = days * travelers * 620;
        int high = days * travelers * 980;

        StringBuilder builder = new StringBuilder();
        builder.append("Assumption: ").append(days).append(" day(s), ").append(travelers).append(" traveler(s).").append('\n');
        builder.append("Estimated budget range (CNY): low=").append(low)
                .append(", medium=").append(medium)
                .append(", high=").append(high).append('.');

        if (userBudget > 0) {
            builder.append('\n');
            if (userBudget < low) {
                builder.append("Current budget is likely tight; suggest reducing hotel level or itinerary intensity.");
            } else if (userBudget > high) {
                builder.append("Current budget is generous; can upgrade hotels and local experiences.");
            } else {
                builder.append("Current budget is feasible; medium plan is recommended.");
            }
        }
        return builder.toString();
    }

    private int extractInt(Pattern pattern, String text, int defaultValue, int min, int max) {
        if (text == null || text.isBlank()) {
            return defaultValue;
        }
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return defaultValue;
        }
        try {
            int value = Integer.parseInt(matcher.group(1));
            if (value < min) {
                return min;
            }
            return Math.min(value, max);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private String buildRagContext(List<RagReferenceItem> references) {
        if (references == null || references.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        int index = 1;
        for (RagReferenceItem item : references) {
            builder.append('[').append(index++).append("] ")
                    .append(item.documentTitle())
                    .append(" | score=").append(String.format("%.4f", item.score()))
                    .append('\n')
                    .append(item.snippet())
                    .append("\n\n");
        }
        return builder.toString().trim();
    }

    private String fallback(String text, String defaultValue) {
        return text == null || text.isBlank() ? defaultValue : text;
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("role", role);
        item.put("content", content);
        return item;
    }
}
