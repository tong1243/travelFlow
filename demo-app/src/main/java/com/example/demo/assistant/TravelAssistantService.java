package com.example.demo.assistant;

import com.example.demo.assistant.dto.AssistantResult;
import com.example.demo.assistant.dto.PortalSpotPlanRequest;
import com.example.demo.assistant.dto.TravelBudgetRequest;
import com.example.demo.assistant.dto.TravelFollowUpRequest;
import com.example.demo.assistant.dto.TravelPlanByFilesRequest;
import com.example.demo.assistant.dto.TravelPlanRequest;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.function.Consumer;

@Service
public class TravelAssistantService {

    private static final String DEFAULT_MAIN_PROMPT_TEMPLATE = """
            你是一名资深旅行规划顾问，擅长行程设计、预算拆解、交通衔接和风险提示。
            当前服务器时间：%s（时区：%s）
            回答要求：
            1) 信息真实可执行，避免空话。
            2) 输出结构化，优先使用分级标题和列表。
            3) 优先考虑省时、省钱、少踩坑。
            4) 默认使用中文。
            """;
    private static final DateTimeFormatter PROMPT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX");

    private final BailianClient bailianClient;
    private final BailianProperties properties;
    private final PromptFileService promptFileService;

    public TravelAssistantService(BailianClient bailianClient,
                                  BailianProperties properties,
                                  PromptFileService promptFileService) {
        this.bailianClient = bailianClient;
        this.properties = properties;
        this.promptFileService = promptFileService;
    }

    public AssistantResult generateTravelPlan(TravelPlanRequest request) {
        int travelers = request.travelers() == null || request.travelers() <= 0 ? 1 : request.travelers();
        String prompt = """
                请为我制定旅行计划，按以下信息：
                - 目的地：%s
                - 出发地：%s
                - 出行日期：%s 到 %s
                - 出行人数：%d
                - 预算：%s
                - 兴趣偏好：%s
                - 旅行风格：%s
                - 补充说明：%s

                输出格式要求：
                1) 行程总览（适合人群、核心亮点、避坑提醒）
                2) 按天行程（上午/下午/晚上）
                3) 每日交通建议（方式、时长、注意事项）
                4) 餐饮与住宿建议（按预算分档）
                5) 总预算估算表（交通/住宿/餐饮/门票/机动）
                6) 打包清单与天气准备建议
                7) 紧急预案（天气变化/延误/门票售罄）
                8) 输出控制在 900-1200 字，避免冗长
                """.formatted(
                request.destination(),
                blankToDefault(request.departureCity(), "未提供"),
                blankToDefault(request.startDate(), "未提供"),
                blankToDefault(request.endDate(), "未提供"),
                travelers,
                blankToDefault(request.budget(), "未提供"),
                blankToDefault(request.interests(), "未提供"),
                blankToDefault(request.travelStyle(), "轻松"),
                blankToDefault(request.notes(), "无")
        );

        String content = bailianClient.chat(properties.getDefaultModel(), buildSystemPrompt(), prompt);
        return new AssistantResult("bailian", properties.getDefaultModel(), content);
    }

    public void streamTravelPlan(TravelPlanRequest request, Consumer<String> onDelta) {
        int travelers = request.travelers() == null || request.travelers() <= 0 ? 1 : request.travelers();
        String prompt = """
                请用中文生成一份实用的结构化旅行方案：
                - 目的地：%s
                - 出发地：%s
                - 日期范围：%s 到 %s
                - 出行人数：%d
                - 预算：%s
                - 兴趣偏好：%s
                - 旅行风格：%s
                - 备注：%s

                输出要求：
                1) 方案总览与适配人群
                2) 逐日安排（上午/下午/晚上）
                3) 交通衔接建议
                4) 预算拆分
                5) 避坑提醒
                """.formatted(
                request.destination(),
                blankToDefault(request.departureCity(), "上海"),
                blankToDefault(request.startDate(), "待定"),
                blankToDefault(request.endDate(), "待定"),
                travelers,
                blankToDefault(request.budget(), "中等预算"),
                blankToDefault(request.interests(), "观光"),
                blankToDefault(request.travelStyle(), "轻松"),
                blankToDefault(request.notes(), "无")
        );
        bailianClient.chatStream(properties.getDefaultModel(), buildSystemPrompt(), prompt, onDelta);
    }

    public AssistantResult estimateBudget(TravelBudgetRequest request) {
        int travelers = request.travelers() == null || request.travelers() <= 0 ? 1 : request.travelers();
        int days = request.days() == null || request.days() <= 0 ? 1 : request.days();
        String prompt = """
                帮我做旅行预算方案：
                - 目的地：%s
                - 天数：%d
                - 人数：%d
                - 币种：%s
                - 期望总预算：%s
                - 旅行风格：%s
                - 备注：%s

                输出格式要求：
                1) 基础版、舒适版预算对比（表格）
                2) 费用拆分（机票、酒店、交通、餐饮、景点、保险）
                3) 可节省成本的 10 条建议（按优先级）
                4) 最终建议预算区间（最低可行 + 推荐）
                5) 输出控制在 700-1000 字
                """.formatted(
                request.destination(),
                days,
                travelers,
                blankToDefault(request.budgetCurrency(), "人民币"),
                blankToDefault(request.expectedBudget(), "未提供"),
                blankToDefault(request.travelStyle(), "平衡"),
                blankToDefault(request.notes(), "无")
        );

        String content = bailianClient.chat(properties.getDefaultModel(), buildSystemPrompt(), prompt);
        return new AssistantResult("bailian", properties.getDefaultModel(), content);
    }

    public AssistantResult planByFiles(TravelPlanByFilesRequest request) {
        List<String> fileIds = request.fileIds().stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .toList();
        if (fileIds.isEmpty()) {
            throw new AssistantException("文件编号列表不能为空");
        }

        String prompt = """
                请基于这些上传文件生成旅行计划。
                文件可能包含：机酒订单、攻略文档、签证材料、偏好说明。
                额外要求：%s

                输出：
                1) 行程建议（按天）
                2) 关键风险和冲突检查（时间/地点/签证/营业时间）
                3) 优化建议（更省时或更省钱的替代方案）
                4) 输出控制在 900 字以内
                """.formatted(blankToDefault(request.requirement(), "请优先保证行程可执行"));

        String content = bailianClient.chatWithFiles(properties.getFileModel(), buildSystemPrompt(), fileIds, prompt);
        return new AssistantResult("bailian", properties.getFileModel(), content);
    }

    public AssistantResult askByFile(String fileId, String question) {
        String prompt = """
                请基于上传文件回答问题，并标注依据。
                如果文件没有直接证据，请明确说明“文件中未找到直接依据”。
                输出控制在 400 字以内。
                问题：%s
                """.formatted(blankToDefault(question, "无"));

        String content = bailianClient.chatWithFiles(
                properties.getFileModel(),
                buildSystemPrompt(),
                List.of(fileId),
                prompt
        );
        return new AssistantResult("bailian", properties.getFileModel(), content);
    }

    public AssistantResult generateSpotPlan(PortalSpotPlanRequest request) {
        int travelers = request.travelers() == null || request.travelers() <= 0 ? 1 : request.travelers();
        String prompt = buildSpotPlanPrompt(request, travelers);
        String content = bailianClient.chat(properties.getDefaultModel(), buildSystemPrompt(), prompt);
        return new AssistantResult("bailian", properties.getDefaultModel(), content);
    }

    public void streamSpotPlan(PortalSpotPlanRequest request, Consumer<String> onDelta) {
        int travelers = request.travelers() == null || request.travelers() <= 0 ? 1 : request.travelers();
        String prompt = buildSpotPlanPrompt(request, travelers);
        bailianClient.chatStream(properties.getDefaultModel(), buildSystemPrompt(), prompt, onDelta);
    }

    public void streamFollowUp(TravelFollowUpRequest request, Consumer<String> onDelta) {
        String prompt = """
                你正在继续一段旅行规划对话。
                上一轮回答如下：
                ---
                %s
                ---

                用户追问：
                %s

                回答要求：
                1) 使用中文并保持结构化格式；
                2) 与上一轮方案保持上下文一致；
                3) 内容具体、可执行；
                4) 若上一轮存在问题，请明确纠正。
                """.formatted(
                blankToDefault(request.previousAnswer(), "无"),
                blankToDefault(request.question(), "")
        );
        bailianClient.chatStream(properties.getDefaultModel(), buildSystemPrompt(), prompt, onDelta);
    }

    private String buildSpotPlanPrompt(PortalSpotPlanRequest request, int travelers) {
        return """
                请围绕以下景点生成实用旅行方案：
                - 景点名称：%s
                - 景点位置：%s
                - 出发地：%s
                - 日期范围：%s 到 %s
                - 出行人数：%d
                - 预算：%s
                - 偏好：%s

                输出要求：
                1) 适配人群与核心亮点
                2) 逐日安排（上午/下午/晚上）
                3) 关键地点之间的交通建议
                4) 预算拆分（交通/住宿/餐饮/门票）
                5) 5 条实用避坑建议
                6) 整体简洁、可执行
                """.formatted(
                blankToDefault(request.title(), "未知景点"),
                blankToDefault(request.location(), "未知位置"),
                blankToDefault(request.departureCity(), "上海"),
                blankToDefault(request.startDate(), "待定"),
                blankToDefault(request.endDate(), "待定"),
                travelers,
                blankToDefault(request.budget(), "中等预算"),
                blankToDefault(request.preference(), "轻松")
        );
    }

    private String blankToDefault(String text, String defaultValue) {
        return text == null || text.isBlank() ? defaultValue : text;
    }

    private String buildSystemPrompt() {
        ZoneId zone = ZoneId.systemDefault();
        String now = OffsetDateTime.now(zone).format(PROMPT_TIME_FORMATTER);
        String template = promptFileService.loadOrDefault("main_prompt.txt", DEFAULT_MAIN_PROMPT_TEMPLATE);
        return renderPromptWithTime(template, now, zone.getId());
    }

    private String renderPromptWithTime(String template, String now, String zoneId) {
        String rendered = template
                .replace("{{now}}", now)
                .replace("{{timezone}}", zoneId);
        if (rendered.contains("%s")) {
            try {
                return rendered.formatted(now, zoneId);
            } catch (IllegalFormatException ex) {
                // 若用户提示词包含未转义百分号，回退到安全拼接，避免请求失败。
                return rendered + System.lineSeparator() + "当前服务器时间：" + now + "（时区：" + zoneId + "）";
            }
        }
        return rendered;
    }
}
