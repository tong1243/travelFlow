package com.example.demo.rag.service.agent;

import com.example.demo.rag.dto.AgentToolTrace;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Order(38)
/**
 * PlanScoringTool 类。
 * 作为智能体工具节点参与编排流程，负责触发判断、执行与轨迹写入。
 * 通过与 AgentToolRuntime 协作，将中间结果沉淀为后续推理可消费的数据。
 */
public class PlanScoringTool implements AgentTool {

    private static final Pattern BUDGET_PATTERN = Pattern.compile("(\\d{3,8})");

    @Override
    /**
     * 返回工具在编排链路中的名称。
     * 该名称会写入轨迹与日志，用于排障、评估和前端可视化展示。
     * 建议保持语义稳定，避免影响已有监控和调用方解析。
     * @return 工具名称字符串，用于链路追踪与前端展示。
     */
    public String toolName() {
        return "方案评分";
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
        PlanScoreBundle bundle = buildScores(runtime);
        String summary = renderSummary(bundle);
        runtime.setPlanScoreSummary(summary);
        runtime.addTrace(toolName(), buildInputSummary(runtime), summary);
    }

    /**
     * 构建 buildInputSummary 所需的静态映射或聚合结构。
     * 将常用城市、站点或关键词进行集中维护，便于统一复用。
     * 构建结果通常为不可变集合，避免运行时被意外修改。
     * @param runtime 当前轮次运行时对象，用于读写工具轨迹和中间结果。
     * @return 构建完成的映射或集合对象。
     */
    private String buildInputSummary(AgentToolRuntime runtime) {
        String question = fallback(runtime.getQuestion(), "");
        int budget = extractBudget(question);
        int refCount = runtime.getReferences() == null ? 0 : runtime.getReferences().size();
        int traceCount = runtime.getTraces() == null ? 0 : runtime.getTraces().size();
        return "预算估计=" + (budget > 0 ? budget : "未知")
                + "，引用数=" + refCount
                + "，已执行工具数=" + traceCount;
    }

    /**
     * 构建 buildScores 所需的静态映射或聚合结构。
     * 将常用城市、站点或关键词进行集中维护，便于统一复用。
     * 构建结果通常为不可变集合，避免运行时被意外修改。
     * @param runtime 当前轮次运行时对象，用于读写工具轨迹和中间结果。
     * @return 构建完成的映射或集合对象。
     */
    private PlanScoreBundle buildScores(AgentToolRuntime runtime) {
        String question = fallback(runtime.getQuestion(), "").toLowerCase();
        boolean hasFlight = toolExecuted(runtime.getTraces(), "机票查询") || containsAny(question, "机票", "航班", "flight");
        boolean hasTrain = toolExecuted(runtime.getTraces(), "车票查询") || containsAny(question, "高铁", "火车", "车票", "train");
        boolean hasHotel = toolExecuted(runtime.getTraces(), "酒店查询") || containsAny(question, "酒店", "住宿", "民宿", "hotel");
        boolean hasWeather = toolExecuted(runtime.getTraces(), "天气查询");
        int budget = extractBudget(question);
        int budgetLevel = resolveBudgetLevel(budget); // 1=低预算,2=中预算,3=高预算

        PlanScore optionA = scoreBalanced(hasFlight, hasTrain, hasHotel, hasWeather, budgetLevel);
        PlanScore optionB = scoreEfficiency(hasFlight, hasTrain, hasHotel, hasWeather, budgetLevel);
        PlanScore optionC = scoreBudgetFirst(hasFlight, hasTrain, hasHotel, hasWeather, budgetLevel);

        PlanScore recommended = optionA;
        if (optionB.totalScore() > recommended.totalScore()) {
            recommended = optionB;
        }
        if (optionC.totalScore() > recommended.totalScore()) {
            recommended = optionC;
        }
        String fallbackCondition = "预算明显紧张时切换方案C；行程被压缩到1-2天时切换方案B。";
        return new PlanScoreBundle(List.of(optionA, optionB, optionC), recommended, fallbackCondition);
    }

    /**
     * 执行 scoreBalanced 处理逻辑。
     * 负责当前步骤的核心处理，确保输入被稳定转换为可复用输出。
     * 处理过程中会遵循当前工具的权限、限流和容错约束。
     * @param hasFlight 输入参数 hasFlight。
     * @param hasTrain 输入参数 hasTrain。
     * @param hasHotel 输入参数 hasHotel。
     * @param hasWeather 输入参数 hasWeather。
     * @param budgetLevel 输入参数 budgetLevel。
     * @return 当前步骤处理后的返回结果。
     */
    private PlanScore scoreBalanced(boolean hasFlight, boolean hasTrain, boolean hasHotel, boolean hasWeather, int budgetLevel) {
        int budgetFit = clamp(76 + (budgetLevel == 2 ? 8 : budgetLevel == 1 ? 4 : 6) + (hasTrain ? 3 : 0));
        int efficiency = clamp(75 + (hasFlight ? 8 : 0) + (hasTrain ? 4 : 0));
        int comfort = clamp(74 + (hasHotel ? 10 : 0) + (hasFlight ? 4 : 0));
        int risk = clamp(80 + (hasWeather ? 6 : 0) + (hasHotel ? 2 : 0));
        int total = weightedTotal(budgetFit, efficiency, comfort, risk);
        return new PlanScore("A", "均衡稳妥", total, budgetFit, efficiency, comfort, risk, "兼顾预算、体验与稳定性。");
    }

    /**
     * 执行 scoreEfficiency 处理逻辑。
     * 负责当前步骤的核心处理，确保输入被稳定转换为可复用输出。
     * 处理过程中会遵循当前工具的权限、限流和容错约束。
     * @param hasFlight 输入参数 hasFlight。
     * @param hasTrain 输入参数 hasTrain。
     * @param hasHotel 输入参数 hasHotel。
     * @param hasWeather 输入参数 hasWeather。
     * @param budgetLevel 输入参数 budgetLevel。
     * @return 当前步骤处理后的返回结果。
     */
    private PlanScore scoreEfficiency(boolean hasFlight, boolean hasTrain, boolean hasHotel, boolean hasWeather, int budgetLevel) {
        int budgetFit = clamp(62 + (budgetLevel == 3 ? 10 : 0) + (budgetLevel == 1 ? -6 : 0));
        int efficiency = clamp(80 + (hasFlight ? 12 : 4) + (hasTrain ? 4 : 0));
        int comfort = clamp(72 + (hasHotel ? 8 : 0) + (hasFlight ? 5 : 0));
        int risk = clamp(70 + (hasWeather ? 6 : 0));
        int total = weightedTotal(budgetFit, efficiency, comfort, risk);
        return new PlanScore("B", "效率优先", total, budgetFit, efficiency, comfort, risk, "适合假期较短且行程密集。");
    }

    /**
     * 执行 scoreBudgetFirst 处理逻辑。
     * 负责当前步骤的核心处理，确保输入被稳定转换为可复用输出。
     * 处理过程中会遵循当前工具的权限、限流和容错约束。
     * @param hasFlight 输入参数 hasFlight。
     * @param hasTrain 输入参数 hasTrain。
     * @param hasHotel 输入参数 hasHotel。
     * @param hasWeather 输入参数 hasWeather。
     * @param budgetLevel 输入参数 budgetLevel。
     * @return 当前步骤处理后的返回结果。
     */
    private PlanScore scoreBudgetFirst(boolean hasFlight, boolean hasTrain, boolean hasHotel, boolean hasWeather, int budgetLevel) {
        int budgetFit = clamp(80 + (budgetLevel == 1 ? 12 : budgetLevel == 2 ? 7 : 2) + (hasTrain ? 6 : 0));
        int efficiency = clamp(66 + (hasTrain ? 6 : 0) + (hasFlight ? 2 : 0));
        int comfort = clamp(65 + (hasHotel ? 6 : 0));
        int risk = clamp(78 + (hasWeather ? 6 : 0) + (hasTrain ? 3 : 0));
        int total = weightedTotal(budgetFit, efficiency, comfort, risk);
        return new PlanScore("C", "成本优先", total, budgetFit, efficiency, comfort, risk, "适合预算敏感用户。");
    }

    /**
     * 执行 renderSummary 处理逻辑。
     * 负责当前步骤的核心处理，确保输入被稳定转换为可复用输出。
     * 处理过程中会遵循当前工具的权限、限流和容错约束。
     * @param bundle 输入参数 bundle。
     * @return 当前步骤处理后的返回结果。
     */
    private String renderSummary(PlanScoreBundle bundle) {
        StringBuilder builder = new StringBuilder();
        PlanScore best = bundle.recommended();
        builder.append("方案评估（质检，不是多方案输出）：").append('\n');
        builder.append("当前方案综合分 ").append(best.totalScore())
                .append("（预算").append(best.budgetFit())
                .append(" / 效率").append(best.efficiency())
                .append(" / 舒适").append(best.comfort())
                .append(" / 风险").append(best.risk())
                .append("）").append('\n');
        builder.append("评估结论：").append(best.note()).append('\n');
        builder.append("自动备选触发条件：").append(bundle.fallbackCondition());
        return builder.toString().trim();
    }

    /**
     * 执行 toolExecuted 条件判断。
     * 用于控制流程分支或开关策略，保证行为可预测。
     * 判断逻辑应保持轻量，避免引入额外副作用。
     * @param traces 输入参数 traces。
     * @param toolName 工具名称，用于轨迹展示和调试定位。
     * @return 判断结果：`true` 表示满足条件，`false` 表示不满足条件。
     */
    private boolean toolExecuted(List<AgentToolTrace> traces, String toolName) {
        if (traces == null || traces.isEmpty()) {
            return false;
        }
        for (AgentToolTrace trace : traces) {
            if (trace == null || trace.toolName() == null) {
                continue;
            }
            if (toolName.equals(trace.toolName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从输入文本中提取 extractBudget 所需结构化信息。
     * 通过正则或规则把自然语言转换为后续步骤可消费的字段。
     * 解析失败时返回空值或默认值，并交由上游统一兜底。
     * @param question 用户输入的问题文本。
     * @return 从文本中提取出的结构化结果。
     */
    private int extractBudget(String question) {
        if (question == null || question.isBlank()) {
            return -1;
        }
        Matcher matcher = BUDGET_PATTERN.matcher(question);
        int max = -1;
        while (matcher.find()) {
            try {
                int value = Integer.parseInt(matcher.group(1));
                if (value > max) {
                    max = value;
                }
            } catch (NumberFormatException ignored) {
                // ignore invalid number
            }
        }
        return max;
    }

    /**
     * 执行 resolveBudgetLevel 处理逻辑。
     * 负责当前步骤的核心处理，确保输入被稳定转换为可复用输出。
     * 处理过程中会遵循当前工具的权限、限流和容错约束。
     * @param budget 输入参数 budget。
     * @return 当前步骤处理后的返回结果。
     */
    private int resolveBudgetLevel(int budget) {
        if (budget <= 0) {
            return 2;
        }
        if (budget < 1200) {
            return 1;
        }
        if (budget <= 4000) {
            return 2;
        }
        return 3;
    }

    /**
     * 执行 weightedTotal 处理逻辑。
     * 负责当前步骤的核心处理，确保输入被稳定转换为可复用输出。
     * 处理过程中会遵循当前工具的权限、限流和容错约束。
     * @param budgetFit 输入参数 budgetFit。
     * @param efficiency 输入参数 efficiency。
     * @param comfort 输入参数 comfort。
     * @param risk 输入参数 risk。
     * @return 当前步骤处理后的返回结果。
     */
    private int weightedTotal(int budgetFit, int efficiency, int comfort, int risk) {
        return clamp((int) Math.round(
                budgetFit * 0.30
                        + efficiency * 0.25
                        + comfort * 0.25
                        + risk * 0.20
        ));
    }

    /**
     * 执行 clamp 处理逻辑。
     * 负责当前步骤的核心处理，确保输入被稳定转换为可复用输出。
     * 处理过程中会遵循当前工具的权限、限流和容错约束。
     * @param value 输入参数 value。
     * @return 当前步骤处理后的返回结果。
     */
    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
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
     * 对可空文本执行默认值回退。
     * 当原始值为空或空白时返回兜底文本，避免上游出现空显示。
     * 该方法是摘要拼接的基础保障，减少判空样板代码。
     * @param text 输入参数 text。
     * @param defaultValue 输入参数 defaultValue。
     * @return 当原值为空时回退后的可用文本。
     */
    private String fallback(String text, String defaultValue) {
        return text == null || text.isBlank() ? defaultValue : text;
    }

    /**
     * PlanScore 记录类型。
     * 用于承载一次调用过程中的结构化数据，减少样板代码并提升可读性。
     * 该类型通常作为方法返回值或中间上下文，在工具间安全传递。
     */
    private record PlanScore(String code,
                             String name,
                             int totalScore,
                             int budgetFit,
                             int efficiency,
                             int comfort,
                             int risk,
                             String note) {
    }

    /**
     * PlanScoreBundle 记录类型。
     * 用于承载一次调用过程中的结构化数据，减少样板代码并提升可读性。
     * 该类型通常作为方法返回值或中间上下文，在工具间安全传递。
     */
    private record PlanScoreBundle(List<PlanScore> options, PlanScore recommended, String fallbackCondition) {
    }
}
