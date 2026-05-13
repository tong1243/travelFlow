package com.example.demo.rag.service.agent;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
@Order(40)
/**
 * HighRiskGateTool 类。
 * 作为智能体工具节点参与编排流程，负责触发判断、执行与轨迹写入。
 * 通过与 AgentToolRuntime 协作，将中间结果沉淀为后续推理可消费的数据。
 */
public class HighRiskGateTool implements AgentTool {

    @Override
    /**
     * 返回工具在编排链路中的名称。
     * 该名称会写入轨迹与日志，用于排障、评估和前端可视化展示。
     * 建议保持语义稳定，避免影响已有监控和调用方解析。
     * @return 工具名称字符串，用于链路追踪与前端展示。
     */
    public String toolName() {
        return "高风险闸门";
    }

    @Override
    /**
     * 判断当前工具是否应在本轮触发。
     * 根据上下文状态、用户意图和开关配置做轻量判定。
     * 返回 `false` 时仅跳过执行，不影响后续工具继续运行。
     * @param context 工具执行上下文，包含运行时状态、请求参数和权限开关。
     * @return 判断结果：`true` 表示满足条件，`false` 表示不满足条件。
     */
    public boolean shouldRun(AgentToolExecutionContext context) {
        return true;
    }

    @Override
    /**
     * 执行工具主逻辑并回写运行时状态。
     * 通常会调用内部服务完成查询或计算，再把摘要写入运行时对象。
     * 同时补充工具轨迹，保证每一步处理都可追踪、可解释。
     * @param context 工具执行上下文，包含运行时状态、请求参数和权限开关。
     */
    public void execute(AgentToolExecutionContext context) {
        AgentToolRuntime runtime = context.runtime();
        runtime.setHighRiskChecked(true);

        HighRiskGateDecision decision = evaluateHighRiskGate(runtime.getQuestion(), context.allowHighRiskTools());
        runtime.setHighRiskSummary(decision.summary());
        runtime.setBlockedTools(decision.blockedTools());
        runtime.setGateReason(decision.reason());

        if (!decision.summary().isBlank()) {
            runtime.addTrace(toolName(), "mode=high_risk_gate; check=booking,payment,intents", decision.summary());
        }
    }

    /**
     * 执行 evaluateHighRiskGate 处理逻辑。
     * 负责当前步骤的核心处理，确保输入被稳定转换为可复用输出。
     * 处理过程中会遵循当前工具的权限、限流和容错约束。
     * @param question 用户输入的问题文本。
     * @param allowHighRiskTools 是否允许高风险工具自动执行。
     * @return 当前步骤处理后的返回结果。
     */
    private HighRiskGateDecision evaluateHighRiskGate(String question, boolean allowHighRiskTools) {
        Set<String> intents = detectHighRiskIntents(question);
        if (intents.isEmpty()) {
            return new HighRiskGateDecision("", List.of(), "");
        }

        List<String> intentTools = List.copyOf(intents);
        String joined = String.join(", ", intents);
        if (!allowHighRiskTools) {
            String reason = "检测到高风险工具意图（" + joined
                    + "），已拦截自动执行，请先人工确认。";
            return new HighRiskGateDecision(reason, intentTools, reason);
        }

        String summary = "检测到高风险工具意图（" + joined
                + "），当前未接入实时交易连接器，请人工确认后再调用外部接口。";
        String reason = "检测到高风险工具意图（" + joined
                + "），当前为提示模式，不自动拦截。";
        return new HighRiskGateDecision(summary, List.of(), reason);
    }

    /**
     * 执行 detectHighRiskIntents 处理逻辑。
     * 负责当前步骤的核心处理，确保输入被稳定转换为可复用输出。
     * 处理过程中会遵循当前工具的权限、限流和容错约束。
     * @param question 用户输入的问题文本。
     * @return 当前步骤处理后的返回结果。
     */
    private Set<String> detectHighRiskIntents(String question) {
        if (question == null || question.isBlank()) {
            return Set.of();
        }

        String lower = question.toLowerCase(Locale.ROOT);
        if (!containsAny(lower,
                "预订", "订票", "下单", "购买", "支付", "出票", "抢票", "代订", "订购",
                "book", "reserve", "pay", "purchase")) {
            return Set.of();
        }

        Set<String> tools = new LinkedHashSet<>();
        if (containsAny(lower, "机票", "航班", "flight", "air ticket")) {
            tools.add("机票交易");
        }
        if (containsAny(lower, "车票", "高铁", "火车", "列车", "train", "rail")) {
            tools.add("车票交易");
        }
        if (containsAny(lower, "酒店", "住宿", "hotel")) {
            tools.add("酒店交易");
        }
        if (containsAny(lower, "签证", "visa")) {
            tools.add("签证办理");
        }
        return tools;
    }

    /**
     * 执行 containsAny 条件判断。
     * 用于控制流程分支或开关策略，保证行为可预测。
     * 判断逻辑应保持轻量，避免引入额外副作用。
     * @param text 输入参数 text。
     * @param keys 输入参数 keys。
     * @return 判断结果：`true` 表示满足条件，`false` 表示不满足条件。
     */
    private boolean containsAny(String text, String... keys) {
        for (String key : keys) {
            if (text.contains(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * HighRiskGateDecision 记录类型。
     * 用于承载一次调用过程中的结构化数据，减少样板代码并提升可读性。
     * 该类型通常作为方法返回值或中间上下文，在工具间安全传递。
     */
    private record HighRiskGateDecision(String summary, List<String> blockedTools, String reason) {
    }
}
