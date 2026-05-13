package com.example.demo.rag.service;

import com.example.demo.assistant.BailianClient;
import com.example.demo.assistant.BailianProperties;
import com.example.demo.assistant.PromptFileService;
import com.example.demo.rag.config.RagPipelineProperties;
import com.example.demo.rag.dto.AgentChatRequest;
import com.example.demo.rag.dto.AgentChatResponse;
import com.example.demo.rag.dto.AgentToolTrace;
import com.example.demo.rag.dto.RagReferenceItem;
import com.example.demo.rag.entity.ConversationSession;
import com.example.demo.rag.langchain.RagLangChainComposer;
import com.example.demo.rag.service.agent.AgentTool;
import com.example.demo.rag.service.agent.AgentToolExecutionContext;
import com.example.demo.rag.service.agent.AgentToolRuntime;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@Service
/**
 * RagAgentService类。
 * 该类型负责智能体对话的“流程编排”职责：
 * 1) 管理会话读写；
 * 2) 调度已注册工具；
 * 3) 拼装模型提示词并调用大模型；
 * 4) 统一封装最终响应。
 * 工具具体实现已下沉到独立类，当前类不再承载工具细节算法，便于扩展与维护。
 */
public class RagAgentService {

    private static final String MODE_HYBRID_AUTO = "混合自动";
    private static final String CTRIP_HOTEL_SEARCH_URL = "https://hotels.ctrip.com/hotels/list";
    private static final String BOOKING_SEARCH_URL = "https://www.booking.com/searchresults.zh-cn.html";
    private static final String CTRIP_FLIGHT_SEARCH_URL = "https://flights.ctrip.com/online/channel/domestic";
    private static final String TRAIN_SEARCH_URL = "https://kyfw.12306.cn/otn/leftTicket/init";
    private static final String BAIDU_SEARCH_URL = "https://www.baidu.com/s";
    private static final Set<String> DOMESTIC_CITY_HINTS = Set.of(
            "\u4e2d\u56fd",
            "china",
            "\u5317\u4eac",
            "\u4e0a\u6d77",
            "\u5e7f\u5dde",
            "\u6df1\u5733",
            "\u6210\u90fd",
            "\u91cd\u5e86",
            "\u897f\u5b89",
            "\u6b66\u6c49",
            "\u5357\u4eac",
            "\u676d\u5dde",
            "\u82cf\u5dde",
            "\u9752\u5c9b",
            "\u53a6\u95e8",
            "\u4e09\u4e9a",
            "\u6606\u660e",
            "\u957f\u6c99",
            "\u90d1\u5dde",
            "\u9999\u6e2f",
            "\u6fb3\u95e8",
            "\u53f0\u5317",
            "beijing",
            "shanghai",
            "guangzhou",
            "shenzhen",
            "chengdu",
            "chongqing",
            "xian",
            "xi'an",
            "wuhan",
            "nanjing",
            "hangzhou",
            "suzhou",
            "qingdao",
            "xiamen",
            "sanya",
            "kunming",
            "changsha",
            "zhengzhou",
            "hong kong",
            "hongkong",
            "macau",
            "macao",
            "taipei"
    );
    private static final String DEFAULT_REPORT_PROMPT = """
            输出要求：
            1) 先给可执行方案，再补充理由与证据；
            2) 行程需包含具体餐饮店名、交通换乘、景点预约与游玩路线；
            3) 预算、风险、备选方案要独立分段；
            4) 必须输出一键执行清单和实时数据看板（不可得时明确写未检索到）；
            5) 证据不足时明确说明不确定点并给保守建议。
            """;

    private final ConversationService conversationService;
    private final BailianClient bailianClient;
    private final BailianProperties bailianProperties;
    private final RagPipelineProperties ragPipelineProperties;
    private final RagLangChainComposer ragLangChainComposer;
    private final PromptFileService promptFileService;
    private final List<AgentTool> toolRegistry;

    /**
     * 构造智能体编排服务并注入依赖。
     * 其中 `toolRegistry` 由 Spring 自动收集所有 AgentTool 实现，按注解顺序排序后执行。
     *
     * @param conversationService 会话服务，负责会话解析、上下文读取与消息持久化
     * @param bailianClient 大模型客户端，用于提交消息并获取回答
     * @param bailianProperties 大模型配置，提供默认模型名等参数
     * @param ragPipelineProperties RAG 管道配置，提供检索条数、上下文长度等限制
     * @param ragLangChainComposer 提示词编排器，负责拼装系统/知识/历史消息
     * @param promptFileService 提示词模板服务，负责加载外部 prompt 文件
     * @param toolRegistry 工具注册表，注入所有可执行工具并用于统一调度
     */
    public RagAgentService(ConversationService conversationService,
                           BailianClient bailianClient,
                           BailianProperties bailianProperties,
                           RagPipelineProperties ragPipelineProperties,
                           RagLangChainComposer ragLangChainComposer,
                           PromptFileService promptFileService,
                           List<AgentTool> toolRegistry) {
        this.conversationService = conversationService;
        this.bailianClient = bailianClient;
        this.bailianProperties = bailianProperties;
        this.ragPipelineProperties = ragPipelineProperties;
        this.ragLangChainComposer = ragLangChainComposer;
        this.promptFileService = promptFileService;
        this.toolRegistry = sortTools(toolRegistry);
    }

    /**
     * 智能体主对话入口。
     * 处理流程：
     * 1) 规范化问题并解析会话；
     * 2) 按工具注册顺序执行工具链；
     * 3) 拼装系统提示词与上下文；
     * 4) 调用模型生成回答并写回会话；
     * 5) 返回回答、引用、轨迹与执行透明信息。
     *
     * @param userId 当前登录用户 ID
     * @param isAdmin 是否管理员，用于工具权限判断
     * @param request 对话请求参数
     * @return 智能体统一响应体
     */
    public AgentChatResponse chat(Long userId, boolean isAdmin, AgentChatRequest request) {
        String question = sanitizeQuestion(request.question());
        String toolQuestion = enrichToolQuestion(question, request);
        ConversationSession session = conversationService.resolveSession(userId, request.sessionId(), question);
        conversationService.appendMessage(session.getId(), "user", question);

        int stepLimit = Math.max(toolRegistry.size(), Math.max(1, ragPipelineProperties.getAgentMaxSteps()));
        int topK = normalizeTopK(request.topK());
        boolean allowHighRiskTools = Boolean.TRUE.equals(request.allowHighRiskTools());

        AgentToolRuntime runtime = new AgentToolRuntime(stepLimit, toolQuestion, topK);
        AgentToolExecutionContext toolExecutionContext = new AgentToolExecutionContext(
                runtime,
                request,
                userId,
                isAdmin,
                allowHighRiskTools
        );
        executeToolRegistry(toolExecutionContext, null);

        List<ConversationService.ContextMessage> history = conversationService.getRecentContextMessages(session.getId(), 12);
        String ragContext = buildRagContext(runtime.getReferences());
        List<Map<String, String>> messages = ragLangChainComposer.composeMessages(history, runtime.getReferences(), ragContext);
        messages.add(1, message("system", buildAgentInstruction(runtime)));

        String answer = bailianClient.chatWithMessages(bailianProperties.getDefaultModel(), messages);
        answer = refineDetailIfNeeded(answer, runtime, request);
        answer = normalizeExecutionTimelineLabels(answer);
        answer = appendToolRuntimeBoard(answer, runtime);
        answer = appendQuickLinks(answer, request);
        conversationService.appendMessage(session.getId(), "assistant", answer);

        List<AgentToolTrace> outputTraces = Boolean.FALSE.equals(request.includeTrace()) ? List.of() : runtime.getTraces();
        List<String> executedTools = collectExecutedToolNames(runtime.getTraces());
        return new AgentChatResponse(
                session.getId(),
                answer,
                bailianProperties.getDefaultModel(),
                runtime.getReferences(),
                outputTraces,
                MODE_HYBRID_AUTO,
                executedTools,
                runtime.getBlockedTools(),
                resolveGateReason(runtime),
                runtime.getPlanScoreSummary()
        );
    }

    public AgentChatResponse chatStream(Long userId, boolean isAdmin, AgentChatRequest request, Consumer<String> onDelta) {
        return chatStream(userId, isAdmin, request, onDelta, null);
    }

    public AgentChatResponse chatStream(Long userId,
                                        boolean isAdmin,
                                        AgentChatRequest request,
                                        Consumer<String> onDelta,
                                        Consumer<String> onStatus) {
        String question = sanitizeQuestion(request.question());
        String toolQuestion = enrichToolQuestion(question, request);
        ConversationSession session = conversationService.resolveSession(userId, request.sessionId(), question);
        conversationService.appendMessage(session.getId(), "user", question);

        int stepLimit = Math.max(toolRegistry.size(), Math.max(1, ragPipelineProperties.getAgentMaxSteps()));
        int topK = normalizeTopK(request.topK());
        boolean allowHighRiskTools = Boolean.TRUE.equals(request.allowHighRiskTools());

        AgentToolRuntime runtime = new AgentToolRuntime(stepLimit, toolQuestion, topK);
        AgentToolExecutionContext toolExecutionContext = new AgentToolExecutionContext(
                runtime,
                request,
                userId,
                isAdmin,
                allowHighRiskTools
        );
        emitStatus(onStatus, "正在准备工具链执行...");
        executeToolRegistry(toolExecutionContext, onStatus);
        emitStatus(onStatus, "工具调用完成，正在生成最终行程方案...");

        List<ConversationService.ContextMessage> history = conversationService.getRecentContextMessages(session.getId(), 12);
        String ragContext = buildRagContext(runtime.getReferences());
        List<Map<String, String>> messages = ragLangChainComposer.composeMessages(history, runtime.getReferences(), ragContext);
        messages.add(1, message("system", buildAgentInstruction(runtime)));

        StringBuilder answerBuffer = new StringBuilder();
        bailianClient.chatStreamWithMessages(bailianProperties.getDefaultModel(), messages, chunk -> {
            if (chunk == null || chunk.isEmpty()) {
                return;
            }
            answerBuffer.append(chunk);
            if (onDelta != null) {
                onDelta.accept(chunk);
            }
        });
        String answer = answerBuffer.toString();
        answer = refineDetailIfNeeded(answer, runtime, request);
        answer = normalizeExecutionTimelineLabels(answer);
        answer = appendToolRuntimeBoard(answer, runtime);
        String answerWithLinks = appendQuickLinks(answer, request);
        if (!answerWithLinks.equals(answer) && onDelta != null) {
            String delta = answerWithLinks.startsWith(answer)
                    ? answerWithLinks.substring(answer.length())
                    : ("\n\n" + buildQuickLinksSection(request));
            if (delta != null && !delta.isBlank()) {
                onDelta.accept(delta);
            }
        }
        answer = answerWithLinks;
        conversationService.appendMessage(session.getId(), "assistant", answer);
        emitStatus(onStatus, "行程方案生成完成，正在返回结果...");

        List<AgentToolTrace> outputTraces = Boolean.FALSE.equals(request.includeTrace()) ? List.of() : runtime.getTraces();
        List<String> executedTools = collectExecutedToolNames(runtime.getTraces());
        return new AgentChatResponse(
                session.getId(),
                answer,
                bailianProperties.getDefaultModel(),
                runtime.getReferences(),
                outputTraces,
                MODE_HYBRID_AUTO,
                executedTools,
                runtime.getBlockedTools(),
                resolveGateReason(runtime),
                runtime.getPlanScoreSummary()
        );
    }

    /**
     * 对工具列表按 Spring 规则排序。
     * 该步骤用于确保工具执行顺序稳定（例如先检索，再预算，再闸门）。
     */
    private List<AgentTool> sortTools(List<AgentTool> tools) {
        if (tools == null || tools.isEmpty()) {
            return List.of();
        }
        List<AgentTool> sorted = new ArrayList<>(tools);
        AnnotationAwareOrderComparator.sort(sorted);
        return List.copyOf(sorted);
    }

    /**
     * 执行工具注册表。
     * 调度规则：
     * 1) 按注册顺序依次评估工具；
     * 2) 每步先检查步数上限；
     * 3) 满足触发条件后执行工具。
     */
    private void executeToolRegistry(AgentToolExecutionContext context, Consumer<String> onStatus) {
        for (AgentTool tool : toolRegistry) {
            if (!context.runtime().canRunStep()) {
                break;
            }
            if (!tool.shouldRun(context)) {
                continue;
            }
            emitStatus(onStatus, mapToolStatus(tool.toolName()));
            tool.execute(context);
        }
    }

    private void emitStatus(Consumer<String> onStatus, String message) {
        if (onStatus == null || message == null || message.isBlank()) {
            return;
        }
        onStatus.accept(message.trim());
    }

    private String mapToolStatus(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return "正在执行工具调用...";
        }
        return switch (toolName.trim()) {
            case "知识检索" -> "正在调用 RAG 检索...";
            case "联网搜索兜底" -> "正在联网搜索补充细节...";
            case "机票查询" -> "正在调用航班组件...";
            case "车票查询" -> "正在调用高铁组件...";
            case "酒店查询" -> "正在调用酒店组件...";
            case "预算估算" -> "正在计算预算...";
            case "出行提示" -> "正在生成避坑提示...";
            case "天气查询" -> "正在调用天气组件...";
            case "方案评分" -> "正在进行方案评分...";
            case "高风险闸门" -> "正在执行风险闸门检查...";
            default -> "正在调用" + toolName + "...";
        };
    }

    /**
     * 构建本轮系统指令。
     * 指令中会注入：
     * - 执行模式与步数限制；
     * - 已执行工具轨迹；
     * - 预算/提示/方案评分/闸门摘要；
     * - 报告结构要求与证据约束。
     */
    private String buildAgentInstruction(AgentToolRuntime runtime) {
        StringBuilder builder = new StringBuilder();
        builder.append("你是一个旅行规划智能体，必须先依据工具结果再给出结论。").append('\n');
        builder.append("执行模式：").append(MODE_HYBRID_AUTO).append('\n');
        builder.append("步骤上限：").append(runtime.getStepLimit()).append('\n');
        builder.append("本轮已执行工具：").append('\n');
        for (AgentToolTrace trace : runtime.getTraces()) {
            builder.append("[步骤 ").append(trace.step()).append("] ").append(trace.toolName()).append('\n');
            builder.append("输入：").append(trace.toolInput()).append('\n');
            builder.append("输出：").append(trace.toolOutputSummary()).append('\n');
        }
        if (!runtime.getBudgetSummary().isBlank()) {
            builder.append("预算摘要：").append(runtime.getBudgetSummary()).append('\n');
        }
        if (!runtime.getTipsSummary().isBlank()) {
            builder.append("出行提示摘要：").append(runtime.getTipsSummary()).append('\n');
        }
        if (!runtime.getWeatherSummary().isBlank()) {
            builder.append("天气摘要：").append(runtime.getWeatherSummary()).append('\n');
        }
        if (!runtime.getPlanScoreSummary().isBlank()) {
            builder.append("方案评分摘要：").append(runtime.getPlanScoreSummary()).append('\n');
        }
        if (!runtime.getHighRiskSummary().isBlank()) {
            builder.append("高风险闸门摘要：").append(runtime.getHighRiskSummary()).append('\n');
        }
        String reportPrompt = promptFileService.loadOrDefault("report_prompt.txt", DEFAULT_REPORT_PROMPT)
                .replace("{{tool_mode}}", MODE_HYBRID_AUTO)
                .replace("{{step_limit}}", String.valueOf(runtime.getStepLimit()));
        builder.append(reportPrompt).append('\n');
        builder.append("回答语言与用户提问语言保持一致。").append('\n');
        builder.append("输出必须使用 Markdown 格式（标题、列表、加粗等语法清晰可读）。").append('\n');
        builder.append("输出结构：1) 行程方案（含餐饮店名/交通换乘/景点路线） 2) 预算分配 3) 风险与备选方案 4) 一键执行清单 5) 实时数据看板 6) 最终建议。").append('\n');
        builder.append("如果证据不足，必须明确指出不确定点。");
        return builder.toString();
    }

    /**
     * 规范化检索条数 topK。
     * 当请求未给值或给了非法值时，回退到系统配置值。
     */
    private int normalizeTopK(Integer requestTopK) {
        if (requestTopK == null || requestTopK <= 0) {
            return Math.max(1, ragPipelineProperties.getTopK());
        }
        return requestTopK;
    }

    /**
     * 清洗用户问题文本，避免空值传播。
     */
    private String sanitizeQuestion(String question) {
        if (question == null) {
            return "";
        }
        return question.trim();
    }

    private String enrichToolQuestion(String question, AgentChatRequest request) {
        if (request == null) {
            return question;
        }
        StringBuilder builder = new StringBuilder(fallback(question, ""));
        appendStructuredLine(builder, "departure_city", request.departureCity());
        appendStructuredLine(builder, "destination_city", request.destinationCity());
        appendStructuredLine(builder, "travel_start_date", request.travelStartDate());
        appendStructuredLine(builder, "travel_end_date", request.travelEndDate());
        appendStructuredLine(builder, "travelers", request.travelers() == null ? "" : String.valueOf(request.travelers()));
        appendStructuredLine(builder, "budget", request.budget());
        appendStructuredLine(builder, "companion_type", request.companionType());
        appendStructuredLine(builder, "travel_style", request.travelStyle());
        appendStructuredLine(builder, "travel_mode", request.travelMode());
        appendStructuredLine(builder, "hotel_preference", request.hotelPreference());
        appendStructuredLine(builder, "hotel_price_range", request.hotelPriceRange());
        appendStructuredLine(builder, "weather_city", request.destinationCity());
        return builder.toString().trim();
    }

    private void appendStructuredLine(StringBuilder builder, String key, String value) {
        if (builder == null || key == null || key.isBlank() || value == null || value.isBlank()) {
            return;
        }
        String marker = key + ":";
        if (builder.indexOf(marker) >= 0) {
            return;
        }
        if (!builder.isEmpty() && builder.charAt(builder.length() - 1) != '\n') {
            builder.append('\n');
        }
        builder.append(marker).append(value.trim());
    }

    /**
     * 汇总本轮工具名，去重并保留首次出现顺序。
     */
    private List<String> collectExecutedToolNames(List<AgentToolTrace> traces) {
        if (traces == null || traces.isEmpty()) {
            return List.of();
        }
        Set<String> names = new LinkedHashSet<>();
        for (AgentToolTrace trace : traces) {
            if (trace.toolName() != null && !trace.toolName().isBlank()) {
                names.add(trace.toolName());
            }
        }
        return List.copyOf(names);
    }

    /**
     * 生成用户可见闸门原因文案。
     * 优先展示工具实际输出；若本轮未执行闸门，给出兜底说明。
     */
    private String resolveGateReason(AgentToolRuntime runtime) {
        if (runtime == null) {
            return "未执行高风险闸门。";
        }
        if (runtime.getGateReason() != null && !runtime.getGateReason().isBlank()) {
            return runtime.getGateReason();
        }
        if (!runtime.isHighRiskChecked()) {
            return "步骤上限已达，未执行高风险闸门。";
        }
        return "未触发高风险闸门。";
    }

    /**
     * 组装检索上下文字符串。
     * 按“编号+标题+相关度+摘要”拼接，并受配置的最大字符数限制。
     */
    private String buildRagContext(List<RagReferenceItem> references) {
        if (references == null || references.isEmpty()) {
            return "";
        }
        int contextLimit = Math.max(600, ragPipelineProperties.getContextMaxChars());
        StringBuilder builder = new StringBuilder();
        int index = 1;
        for (RagReferenceItem item : references) {
            String line = "[" + (index++) + "] "
                    + fallback(item.documentTitle(), "未命名")
                    + " | 相关度=" + String.format(Locale.ROOT, "%.4f", item.score())
                    + "\n"
                    + fallback(item.snippet(), "")
                    + "\n\n";
            if (builder.length() + line.length() > contextLimit) {
                break;
            }
            builder.append(line);
        }
        return builder.toString().trim();
    }

    /**
     * 文本兜底，避免展示 null 或空串。
     */
    private String fallback(String text, String defaultValue) {
        return text == null || text.isBlank() ? defaultValue : text;
    }

    private String refineDetailIfNeeded(String answer, AgentToolRuntime runtime, AgentChatRequest request) {
        String base = fallback(answer, "").trim();
        if (base.isBlank()) {
            return base;
        }
        if (hasSufficientDetail(base)) {
            return base;
        }
        try {
            StringBuilder userPrompt = new StringBuilder();
            userPrompt.append("请把下面旅行方案改写成“可直接执行”的高颗粒度版本，必须满足：").append('\n');
            userPrompt.append("1) 餐饮给具体店名（至少2家/天，含商圈或地铁站、人均、是否预约）；").append('\n');
            userPrompt.append("2) 交通给地铁线路号、换乘站、耗时、费用，并给打车备选；").append('\n');
            userPrompt.append("3) 景点给预约方式、推荐到达时段、建议游玩时长、拍照机位；").append('\n');
            userPrompt.append("4) 给出至少5条城市相关避坑提示；").append('\n');
            userPrompt.append("5) 给出一键执行清单（提前7天/提前3天/提前1天/当天）；").append('\n');
            userPrompt.append("6) 保留原行程逻辑，但把内容具体化，不要输出引用编号。").append('\n');
            userPrompt.append("7) 禁止连续重复短语或句子（同一短语连续重复不得超过2次）。").append('\n');
            if (runtime != null && runtime.getTraces() != null && !runtime.getTraces().isEmpty()) {
                userPrompt.append('\n').append("可用工具摘要：").append('\n');
                for (AgentToolTrace trace : runtime.getTraces()) {
                    userPrompt.append("- ").append(fallback(trace.toolName(), "工具"))
                            .append("：")
                            .append(fallback(trace.toolOutputSummary(), ""))
                            .append('\n');
                }
            }
            if (request != null) {
                userPrompt.append('\n').append("用户约束：")
                        .append("目的地=").append(fallback(request.destinationCity(), "未提供"))
                        .append("，出发地=").append(fallback(request.departureCity(), "未提供"))
                        .append("，日期=").append(fallback(request.travelStartDate(), "未提供"))
                        .append("~").append(fallback(request.travelEndDate(), "未提供"))
                        .append("，预算=").append(fallback(request.budget(), "未提供"))
                        .append("，人数=").append(request.travelers() == null ? "未提供" : request.travelers())
                        .append('\n');
            }
            userPrompt.append('\n').append("原始方案：").append('\n').append(base);
            String refined = bailianClient.chat(
                    bailianProperties.getDefaultModel(),
                    "你是旅行方案落地优化助手，只输出中文 Markdown。",
                    userPrompt.toString()
            );
            String refinedText = fallback(refined, "").trim();
            return refinedText.isBlank() ? base : refinedText;
        } catch (Exception ex) {
            return base;
        }
    }

    private boolean hasSufficientDetail(String answer) {
        String text = answer == null ? "" : answer.toLowerCase(Locale.ROOT);
        if (text.isBlank()) {
            return false;
        }
        int score = 0;
        if (text.contains("人均")) {
            score++;
        }
        if (text.contains("换乘") || text.contains("地铁")) {
            score++;
        }
        if (text.contains("线路") || text.contains("号线")) {
            score++;
        }
        if (text.contains("预约")) {
            score++;
        }
        if (text.contains("拍照")) {
            score++;
        }
        if (text.contains("游玩时长") || text.contains("建议时长") || text.contains("小时")) {
            score++;
        }
        return score >= 4;
    }

    private String appendQuickLinks(String answer, AgentChatRequest request) {
        String linkSection = buildQuickLinksSection(request);
        if (linkSection.isBlank()) {
            return fallback(answer, "");
        }
        String normalized = fallback(answer, "").trim();
        if (normalized.contains("## 快捷查询链接")) {
            return normalized;
        }
        if (normalized.isBlank()) {
            return linkSection;
        }
        return normalized + "\n\n" + linkSection;
    }

    private String normalizeExecutionTimelineLabels(String answer) {
        String normalized = fallback(answer, "").trim();
        if (normalized.isBlank()) {
            return normalized;
        }
        return normalized
                .replaceAll("(?i)\\bD-\\s*(\\d{1,2})\\b", "提前$1天")
                .replaceAll("(?i)\\bD\\+\\s*0\\b", "当天")
                .replaceAll("(?i)\\bD0\\b", "当天");
    }

    private String appendToolRuntimeBoard(String answer, AgentToolRuntime runtime) {
        String normalized = fallback(answer, "").trim();
        if (runtime == null) {
            return normalized;
        }
        if (normalized.contains("## 工具回填看板")) {
            return normalized;
        }
        String section = buildToolRuntimeBoardSection(runtime);
        if (section.isBlank()) {
            return normalized;
        }
        if (normalized.isBlank()) {
            return section;
        }
        return normalized + "\n\n" + section;
    }

    private String buildToolRuntimeBoardSection(AgentToolRuntime runtime) {
        List<AgentToolTrace> traces = runtime.getTraces();
        if (traces == null || traces.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("## 工具回填看板").append('\n');
        builder.append("- 机票/航班：").append(summarizeToolTrace(traces, "机票查询")).append('\n');
        builder.append("- 高铁/火车：").append(summarizeToolTrace(traces, "车票查询")).append('\n');
        builder.append("- 酒店住宿：").append(summarizeToolTrace(traces, "酒店查询")).append('\n');
        builder.append("- 天气预报：").append(summarizeToolTrace(traces, "天气查询"));
        return builder.toString().trim();
    }

    private String summarizeToolTrace(List<AgentToolTrace> traces, String toolName) {
        String output = findLatestTraceOutput(traces, toolName);
        if (output.isBlank()) {
            return "未调用。";
        }
        if (containsAny(output, "失败", "未查询到", "未匹配", "不可用", "超时")) {
            return "已调用，但暂未获取到有效数据。";
        }
        String first = output.split("\\r?\\n")[0].trim();
        if (first.length() > 68) {
            first = first.substring(0, 68) + "...";
        }
        return "已查询。 " + first;
    }

    private String findLatestTraceOutput(List<AgentToolTrace> traces, String toolName) {
        if (traces == null || traces.isEmpty() || toolName == null || toolName.isBlank()) {
            return "";
        }
        for (int i = traces.size() - 1; i >= 0; i--) {
            AgentToolTrace trace = traces.get(i);
            if (trace == null || trace.toolName() == null) {
                continue;
            }
            if (toolName.equals(trace.toolName().trim())) {
                return fallback(trace.toolOutputSummary(), "");
            }
        }
        return "";
    }

    private boolean containsAny(String text, String... keywords) {
        String normalized = fallback(text, "").toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank() && normalized.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String buildQuickLinksSection(AgentChatRequest request) {
        if (request == null) {
            return "";
        }
        String departureCity = normalizeToken(request.departureCity());
        String destinationCity = normalizeToken(request.destinationCity());
        String travelStartDate = normalizeToken(request.travelStartDate());
        String travelEndDate = normalizeToken(request.travelEndDate());
        String hotelPriceRange = normalizeToken(request.hotelPriceRange());
        if (!travelStartDate.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
            travelStartDate = "";
        }
        if (!travelEndDate.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
            travelEndDate = travelStartDate;
        }

        List<String> lines = new ArrayList<>();
        lines.add("## 快捷查询链接");

        if (!destinationCity.isBlank()) {
            lines.add("- 天气：[查看天气详情](" + buildWeatherSearchLink(destinationCity, travelStartDate) + ")");
        }

        if (!departureCity.isBlank() && !destinationCity.isBlank() && !travelStartDate.isBlank()) {
            lines.add("- 车票：[12306 官方查询](" + buildTrainSearchLink(departureCity, destinationCity, travelStartDate) + ")");
            lines.add("- 机票：[携程机票查询](" + buildFlightSearchLink(departureCity, destinationCity, travelStartDate) + ")");
        }

        if (!destinationCity.isBlank()) {
            boolean domesticDestination = isDomesticDestination(destinationCity);
            String hotelPlatform = domesticDestination ? "携程酒店" : "Booking 酒店";
            String hotelUrl = domesticDestination
                    ? buildCtripHotelLink(destinationCity, travelStartDate, travelEndDate, hotelPriceRange)
                    : buildBookingHotelLink(destinationCity, travelStartDate, travelEndDate, hotelPriceRange);
            lines.add("- 酒店：[" + hotelPlatform + "](" + hotelUrl + ")");
        }

        return String.join("\n", lines);
    }

    private String buildWeatherSearchLink(String destinationCity, String travelStartDate) {
        String query = destinationCity + " 天气";
        if (!travelStartDate.isBlank()) {
            query = query + " " + travelStartDate;
        }
        return UriComponentsBuilder.fromUriString(BAIDU_SEARCH_URL)
                .queryParam("wd", query)
                .toUriString();
    }

    private String buildTrainSearchLink(String departureCity, String destinationCity, String travelStartDate) {
        return UriComponentsBuilder.fromUriString(TRAIN_SEARCH_URL)
                .queryParam("linktypeid", "dc")
                .queryParam("fs", departureCity)
                .queryParam("ts", destinationCity)
                .queryParam("date", travelStartDate)
                .queryParam("flag", "N,N,Y")
                .toUriString();
    }

    private String buildFlightSearchLink(String departureCity, String destinationCity, String travelStartDate) {
        return UriComponentsBuilder.fromUriString(CTRIP_FLIGHT_SEARCH_URL)
                .queryParam("dcity", departureCity)
                .queryParam("acity", destinationCity)
                .queryParam("ddate", travelStartDate)
                .toUriString();
    }

    private String buildCtripHotelLink(String destinationCity, String travelStartDate, String travelEndDate, String hotelPriceRange) {
        String keyword = destinationCity + " 酒店";
        if (hotelPriceRange != null && !hotelPriceRange.isBlank()) {
            keyword = keyword + " " + hotelPriceRange + " CNY";
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(CTRIP_HOTEL_SEARCH_URL)
                .queryParam("cityName", destinationCity)
                .queryParam("keyword", keyword);
        if (!travelStartDate.isBlank()) {
            builder.queryParam("checkin", travelStartDate);
        }
        if (!travelEndDate.isBlank()) {
            builder.queryParam("checkout", travelEndDate);
        }
        return builder.toUriString();
    }

    private String buildBookingHotelLink(String destinationCity, String travelStartDate, String travelEndDate, String hotelPriceRange) {
        String keyword = destinationCity + " hotel";
        if (hotelPriceRange != null && !hotelPriceRange.isBlank()) {
            keyword = keyword + " " + hotelPriceRange + " CNY";
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(BOOKING_SEARCH_URL)
                .queryParam("ss", keyword)
                .queryParam("group_adults", 2)
                .queryParam("no_rooms", 1);
        if (!travelStartDate.isBlank()) {
            builder.queryParam("checkin", travelStartDate);
        }
        if (!travelEndDate.isBlank()) {
            builder.queryParam("checkout", travelEndDate);
        }
        return builder.toUriString();
    }

    private boolean isDomesticDestination(String destinationCity) {
        String normalized = normalizeToken(destinationCity).toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return false;
        }
        if (DOMESTIC_CITY_HINTS.contains(normalized)) {
            return true;
        }
        if (normalized.contains("china") || normalized.contains("\u4e2d\u56fd")) {
            return true;
        }
        return normalized.chars().anyMatch(ch -> ch > 127);
    }

    private String normalizeToken(String text) {
        if (text == null) {
            return "";
        }
        return text.trim();
    }

    /**
     * 构造模型消息对象。
     * 当前接口固定使用 role/content 双字段。
     */
    private Map<String, String> message(String role, String content) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("role", role);
        item.put("content", content);
        return item;
    }
}
