package com.example.demo.assistant;

import com.example.demo.assistant.dto.AssistantResult;
import com.example.demo.assistant.dto.PortalSpotPlanRequest;
import com.example.demo.assistant.dto.TravelBudgetRequest;
import com.example.demo.assistant.dto.TravelFollowUpRequest;
import com.example.demo.assistant.dto.TravelPlanByFilesRequest;
import com.example.demo.assistant.dto.TravelPlanRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;

@Service
public class TravelAssistantService {

    private static final String TRAVEL_SYSTEM_PROMPT = """
            你是一名资深旅行规划顾问，擅长行程设计、预算拆解、交通衔接和风险提示。
            回答要求：
            1) 信息真实可执行，避免空话。
            2) 输出结构化，优先使用 Markdown 标题与列表。
            3) 优先考虑省时、省钱、少踩坑。
            4) 默认使用中文。
            """;

    private final BailianClient bailianClient;
    private final BailianProperties properties;

    public TravelAssistantService(BailianClient bailianClient, BailianProperties properties) {
        this.bailianClient = bailianClient;
        this.properties = properties;
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

        String content = bailianClient.chat(properties.getDefaultModel(), TRAVEL_SYSTEM_PROMPT, prompt);
        return new AssistantResult("bailian", properties.getDefaultModel(), content);
    }

    public void streamTravelPlan(TravelPlanRequest request, Consumer<String> onDelta) {
        int travelers = request.travelers() == null || request.travelers() <= 0 ? 1 : request.travelers();
        String prompt = """
                Please generate a practical travel plan in Chinese with Markdown format:
                - Destination: %s
                - Departure city: %s
                - Date range: %s to %s
                - Travelers: %d
                - Budget: %s
                - Interests: %s
                - Travel style: %s
                - Notes: %s

                Output requirements:
                1) Overview and suitability
                2) Day-by-day schedule (morning/afternoon/evening)
                3) Transport recommendations
                4) Budget breakdown
                5) Anti-pitfall tips
                """.formatted(
                request.destination(),
                blankToDefault(request.departureCity(), "Shanghai"),
                blankToDefault(request.startDate(), "TBD"),
                blankToDefault(request.endDate(), "TBD"),
                travelers,
                blankToDefault(request.budget(), "medium"),
                blankToDefault(request.interests(), "sightseeing"),
                blankToDefault(request.travelStyle(), "relaxed"),
                blankToDefault(request.notes(), "none")
        );
        bailianClient.chatStream(properties.getDefaultModel(), TRAVEL_SYSTEM_PROMPT, prompt, onDelta);
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
                1) 基础版/舒适版预算对比（表格）
                2) 费用拆分（机票、酒店、交通、餐饮、景点、保险）
                3) 可节省成本的 10 条建议（按优先级）
                4) 最终建议预算区间（最低可行 + 推荐）
                5) 输出控制在 700-1000 字
                """.formatted(
                request.destination(),
                days,
                travelers,
                blankToDefault(request.budgetCurrency(), "CNY"),
                blankToDefault(request.expectedBudget(), "未提供"),
                blankToDefault(request.travelStyle(), "平衡"),
                blankToDefault(request.notes(), "无")
        );

        String content = bailianClient.chat(properties.getDefaultModel(), TRAVEL_SYSTEM_PROMPT, prompt);
        return new AssistantResult("bailian", properties.getDefaultModel(), content);
    }

    public AssistantResult planByFiles(TravelPlanByFilesRequest request) {
        List<String> fileIds = request.fileIds().stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .toList();
        if (fileIds.isEmpty()) {
            throw new AssistantException("fileIds 不能为空");
        }

        String prompt = """
                请基于这些上传文件生成旅行计划。
                文件可能包含：机酒预订单、攻略文档、签证材料、偏好说明。

                额外要求：%s

                输出：
                1) 行程建议（按天）
                2) 关键风险和冲突检查（时间/地点/签证/营业时间）
                3) 优化建议（更省时或更省钱的替代方案）
                4) 输出控制在 900 字以内
                """.formatted(blankToDefault(request.requirement(), "请优先保证行程可执行"));

        String content = bailianClient.chatWithFiles(properties.getFileModel(), TRAVEL_SYSTEM_PROMPT, fileIds, prompt);
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
                TRAVEL_SYSTEM_PROMPT,
                List.of(fileId),
                prompt
        );
        return new AssistantResult("bailian", properties.getFileModel(), content);
    }

    public AssistantResult generateSpotPlan(PortalSpotPlanRequest request) {
        int travelers = request.travelers() == null || request.travelers() <= 0 ? 1 : request.travelers();
        String prompt = buildSpotPlanPrompt(request, travelers);
        String content = bailianClient.chat(properties.getDefaultModel(), TRAVEL_SYSTEM_PROMPT, prompt);
        return new AssistantResult("bailian", properties.getDefaultModel(), content);
    }

    public void streamSpotPlan(PortalSpotPlanRequest request, Consumer<String> onDelta) {
        int travelers = request.travelers() == null || request.travelers() <= 0 ? 1 : request.travelers();
        String prompt = buildSpotPlanPrompt(request, travelers);
        bailianClient.chatStream(properties.getDefaultModel(), TRAVEL_SYSTEM_PROMPT, prompt, onDelta);
    }

    public void streamFollowUp(TravelFollowUpRequest request, Consumer<String> onDelta) {
        String prompt = """
                You are continuing a travel assistant conversation.
                Previous answer:
                ---
                %s
                ---

                User follow-up question:
                %s

                Requirements:
                1) Answer in Chinese with Markdown.
                2) Keep context consistent with previous plan.
                3) Be specific and executable.
                4) If previous answer has issues, correct them explicitly.
                """.formatted(
                blankToDefault(request.previousAnswer(), "N/A"),
                blankToDefault(request.question(), "")
        );
        bailianClient.chatStream(properties.getDefaultModel(), TRAVEL_SYSTEM_PROMPT, prompt, onDelta);
    }

    private String buildSpotPlanPrompt(PortalSpotPlanRequest request, int travelers) {
        return """
                Please create a practical travel plan focused on this spot:
                - Spot title: %s
                - Spot location: %s
                - Departure city: %s
                - Date range: %s to %s
                - Travelers: %d
                - Budget: %s
                - Preference: %s

                Output requirements:
                1) Suitable traveler profile and highlight of this spot
                2) Day-by-day schedule (morning/afternoon/evening)
                3) Transport suggestion between key locations
                4) Budget breakdown (transport/hotel/food/tickets)
                5) 5 practical anti-pitfall tips
                6) Keep answer concise and executable
                """.formatted(
                blankToDefault(request.title(), "Unknown spot"),
                blankToDefault(request.location(), "Unknown location"),
                blankToDefault(request.departureCity(), "Shanghai"),
                blankToDefault(request.startDate(), "TBD"),
                blankToDefault(request.endDate(), "TBD"),
                travelers,
                blankToDefault(request.budget(), "medium"),
                blankToDefault(request.preference(), "relaxed")
        );
    }

    private String blankToDefault(String text, String defaultValue) {
        return text == null || text.isBlank() ? defaultValue : text;
    }
}
