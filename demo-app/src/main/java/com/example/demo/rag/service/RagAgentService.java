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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RagAgentService {

    private static final Pattern DAY_PATTERN = Pattern.compile("(\\d{1,2})\\s*(天|day|days)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRAVELER_PATTERN = Pattern.compile("(\\d{1,2})\\s*(人|位|traveler|travelers|people)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BUDGET_PATTERN = Pattern.compile("(\\d{3,8})\\s*(元|块|rmb|cny|¥)?", Pattern.CASE_INSENSITIVE);

    private static final String MODE_MANUAL = "manual";
    private static final String MODE_HYBRID_AUTO = "hybrid_auto";

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
        String toolMode = normalizeToolMode(request.toolMode());
        boolean allowHighRiskTools = Boolean.TRUE.equals(request.allowHighRiskTools());
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
                "topK=" + topK + ", sourceType=" + fallback(request.sourceType(), "不限") + ", sourceRefContains=" + fallback(request.sourceRefContains(), "不限"),
                "检索到 " + references.size() + " 条参考资料"
        ));

        String budgetSummary = "";
        String tipsSummary = "";
        if (MODE_MANUAL.equals(toolMode)) {
            if (stepCounter <= stepLimit && shouldRunBudgetEstimator(request.question())) {
                budgetSummary = estimateBudgetFromQuestion(request.question());
                traces.add(new AgentToolTrace(
                        stepCounter++,
                        "budget_estimator",
                        request.question(),
                        budgetSummary
                ));
            }
        } else {
            if (stepCounter <= stepLimit && shouldRunBudgetEstimator(request.question())) {
                budgetSummary = estimateBudgetFromQuestion(request.question());
                traces.add(new AgentToolTrace(
                        stepCounter++,
                        "budget_estimator",
                        request.question(),
                        budgetSummary
                ));
            }

            if (stepCounter <= stepLimit && shouldRunTravelTips(request.question())) {
                tipsSummary = buildTravelTips(request.question());
                traces.add(new AgentToolTrace(
                        stepCounter++,
                        "travel_tips",
                        request.question(),
                        tipsSummary
                ));
            }

            if (stepCounter <= stepLimit) {
                String highRiskGate = buildHighRiskGateSummary(request.question(), allowHighRiskTools);
                if (!highRiskGate.isBlank()) {
                    traces.add(new AgentToolTrace(
                            stepCounter++,
                            "high_risk_gate",
                            request.question(),
                            highRiskGate
                    ));
                }
            }
        }

        List<ConversationService.ContextMessage> history = conversationService.getRecentContextMessages(session.getId(), 12);
        String ragContext = buildRagContext(references);
        List<Map<String, String>> messages = ragLangChainComposer.composeMessages(history, references, ragContext);
        messages.add(1, message("system", buildAgentInstruction(toolMode, stepLimit, traces, budgetSummary, tipsSummary)));

        String answer = bailianClient.chatWithMessages(bailianProperties.getDefaultModel(), messages);
        conversationService.appendMessage(session.getId(), "assistant", answer);

        List<AgentToolTrace> outputTraces = Boolean.FALSE.equals(request.includeTrace()) ? List.of() : traces;
        return new AgentChatResponse(session.getId(), answer, bailianProperties.getDefaultModel(), references, outputTraces);
    }

    private String normalizeToolMode(String toolMode) {
        if (toolMode == null || toolMode.isBlank()) {
            return MODE_HYBRID_AUTO;
        }
        String normalized = toolMode.trim().toLowerCase();
        if (MODE_MANUAL.equals(normalized)) {
            return MODE_MANUAL;
        }
        return MODE_HYBRID_AUTO;
    }

    private String buildAgentInstruction(String toolMode,
                                         int stepLimit,
                                         List<AgentToolTrace> traces,
                                         String budgetSummary,
                                         String tipsSummary) {
        StringBuilder builder = new StringBuilder();
        builder.append("当前处于 Agent 执行模式。\n");
        builder.append("- 工具调度模式：").append(toolMode).append('\n');
        builder.append("- 最大步骤数：").append(stepLimit).append('\n');
        builder.append("- 本轮已执行工具：\n");
        for (AgentToolTrace trace : traces) {
            builder.append("  [步骤 ").append(trace.step()).append("] ").append(trace.toolName()).append('\n');
            builder.append("  输入：").append(trace.toolInput()).append('\n');
            builder.append("  输出：").append(trace.toolOutputSummary()).append('\n');
        }
        if (!budgetSummary.isBlank()) {
            builder.append("- 预算估算结果：\n").append(budgetSummary).append('\n');
        }
        if (!tipsSummary.isBlank()) {
            builder.append("- 出行提醒结果：\n").append(tipsSummary).append('\n');
        }
        builder.append("- 最终回答请包含：可执行行程、费用说明、风险提示，并在关键信息处标注引用 [1][2]。");
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

        int low = days * travelers * 320;
        int medium = days * travelers * 620;
        int high = days * travelers * 980;

        StringBuilder builder = new StringBuilder();
        builder.append("假设条件：").append(days).append(" 天，").append(travelers).append(" 人。\n");
        builder.append("预算区间估算（人民币）：低配=").append(low)
                .append("，中配=").append(medium)
                .append("，高配=").append(high).append("。");

        if (userBudget > 0) {
            builder.append('\n');
            if (userBudget < low) {
                builder.append("当前预算偏紧，建议降低酒店档位或减少高成本项目。");
            } else if (userBudget > high) {
                builder.append("当前预算较充足，可提升住宿标准并增加本地体验项目。");
            } else {
                builder.append("当前预算基本可行，建议按中配方案执行。");
            }
        }
        return builder.toString();
    }

    private boolean shouldRunTravelTips(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        String lower = question.toLowerCase();
        return lower.contains("注意")
                || lower.contains("避坑")
                || lower.contains("安全")
                || lower.contains("行李")
                || lower.contains("清单")
                || lower.contains("准备")
                || lower.contains("tips");
    }

    private String buildTravelTips(String question) {
        return """
                出行提醒：
                1) 证件与票据分开放置，并保留电子备份。
                2) 行程至少预留 10%% 机动时间，避免转场过紧。
                3) 住宿优先选交通便利区域，减少通勤损耗。
                4) 高峰景点尽量错峰预约，避免排队影响体验。
                5) 预算建议按 70%% 固定支出 + 20%%弹性支出 + 10%%应急支出管理。
                """;
    }

    private String buildHighRiskGateSummary(String question, boolean allowHighRiskTools) {
        Set<String> intents = detectHighRiskIntents(question);
        if (intents.isEmpty()) {
            return "";
        }

        String joined = String.join("、", intents);
        if (!allowHighRiskTools) {
            return "检测到高风险工具意图（" + joined + "），已按混合自动策略拦截自动调用；如需执行，请先人工确认。";
        }
        return "检测到高风险工具意图（" + joined + "），但当前服务尚未接入对应实时数据源，建议人工确认后再调用外部 API。";
    }

    private Set<String> detectHighRiskIntents(String question) {
        if (question == null || question.isBlank()) {
            return Set.of();
        }
        String lower = question.toLowerCase();
        Set<String> tools = new LinkedHashSet<>();

        if (containsAny(lower, "机票", "航班", "flight")) {
            tools.add("search_flights");
        }
        if (containsAny(lower, "高铁", "火车", "train")) {
            tools.add("search_trains");
        }
        if (containsAny(lower, "酒店", "住宿", "hotel")) {
            tools.add("search_hotels");
        }
        if (containsAny(lower, "签证", "visa")) {
            tools.add("query_visa_info");
        }
        if (containsAny(lower, "天气", "weather")) {
            tools.add("get_weather");
        }
        return tools;
    }

    private boolean containsAny(String text, String... keys) {
        for (String key : keys) {
            if (text.contains(key)) {
                return true;
            }
        }
        return false;
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
                    .append(" | 相关度=").append(String.format("%.4f", item.score()))
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

