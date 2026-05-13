package com.example.demo.rag.service.agent;

import com.example.demo.rag.dto.AgentToolTrace;
import com.example.demo.rag.dto.RagReferenceItem;

import java.util.ArrayList;
import java.util.List;

/**
 * AgentToolRuntime 类。
 * 承担当前模块的核心处理逻辑，并向上层提供稳定、可测试的调用入口。
 * 通过清晰的职责边界降低耦合，便于后续扩展和维护。
 */
public class AgentToolRuntime {

    private final int stepLimit;
    private final String question;
    private final int topK;
    private int stepCounter = 1;
    private final List<AgentToolTrace> traces = new ArrayList<>();
    private List<RagReferenceItem> references = List.of();
    private String budgetSummary = "";
    private String tipsSummary = "";
    private String weatherSummary = "";
    private String planScoreSummary = "";
    private String highRiskSummary = "";
    private List<String> blockedTools = List.of();
    private String gateReason = "";
    private boolean highRiskChecked = false;

    /**
     * 构造并初始化 AgentToolRuntime 对象。
     * 通过依赖注入完成必需组件装配，确保实例创建后即可参与完整流程。
     * 初始化阶段不会触发业务副作用，仅完成运行准备。
     * @param stepLimit 输入参数 stepLimit。
     * @param question 用户输入的问题文本。
     * @param topK 输入参数 topK。
     */
    public AgentToolRuntime(int stepLimit, String question, int topK) {
        this.stepLimit = stepLimit;
        this.question = question;
        this.topK = topK;
    }

    /**
     * 执行 canRunStep 条件判断。
     * 用于控制流程分支或开关策略，保证行为可预测。
     * 判断逻辑应保持轻量，避免引入额外副作用。
     * @return 判断结果：`true` 表示满足条件，`false` 表示不满足条件。
     */
    public boolean canRunStep() {
        return stepCounter <= stepLimit;
    }

    /**
     * 记录当前工具执行轨迹并推进步骤计数。
     * 会对输入输出摘要进行长度裁剪，防止轨迹过长影响可读性。
     * 超过步数上限时自动忽略写入，保证执行边界一致。
     * @param toolName 工具名称，用于轨迹展示和调试定位。
     * @param input 工具输入摘要。
     * @param output 工具输出摘要。
     */
    public void addTrace(String toolName, String input, String output) {
        if (!canRunStep()) {
            return;
        }
        int outputLimit = resolveOutputClipLimit(toolName);
        traces.add(new AgentToolTrace(
                stepCounter++,
                toolName,
                clip(input, 260),
                clip(output, outputLimit)
        ));
    }

    private int resolveOutputClipLimit(String toolName) {
        String name = toolName == null ? "" : toolName.trim();
        if (name.contains("车票查询") || name.contains("酒店查询") || name.contains("天气查询")) {
            return 6000;
        }
        if (name.contains("机票查询")) {
            return 1200;
        }
        return 420;
    }

    /**
     * 获取 StepLimit 当前值。
     * 提供只读访问入口，避免调用方直接依赖内部实现细节。
     * 返回值可被上层流程直接消费或继续加工。
     * @return 当前步骤处理后的返回结果。
     */
    public int getStepLimit() {
        return stepLimit;
    }

    /**
     * 获取 Question 当前值。
     * 提供只读访问入口，避免调用方直接依赖内部实现细节。
     * 返回值可被上层流程直接消费或继续加工。
     * @return 当前步骤处理后的返回结果。
     */
    public String getQuestion() {
        return question;
    }

    /**
     * 获取 TopK 当前值。
     * 提供只读访问入口，避免调用方直接依赖内部实现细节。
     * 返回值可被上层流程直接消费或继续加工。
     * @return 当前步骤处理后的返回结果。
     */
    public int getTopK() {
        return topK;
    }

    /**
     * 获取 Traces 当前值。
     * 提供只读访问入口，避免调用方直接依赖内部实现细节。
     * 返回值可被上层流程直接消费或继续加工。
     * @return 当前步骤处理后的返回结果。
     */
    public List<AgentToolTrace> getTraces() {
        return traces;
    }

    /**
     * 获取 References 当前值。
     * 提供只读访问入口，避免调用方直接依赖内部实现细节。
     * 返回值可被上层流程直接消费或继续加工。
     * @return 当前步骤处理后的返回结果。
     */
    public List<RagReferenceItem> getReferences() {
        return references;
    }

    /**
     * 设置 References 的值。
     * 统一写入路径，便于后续扩展校验、审计或联动逻辑。
     * 对空值输入会执行安全兜底，避免状态污染。
     * @param references 参考资料列表，用于拼接回答依据。
     */
    public void setReferences(List<RagReferenceItem> references) {
        this.references = references == null ? List.of() : references;
    }

    /**
     * 获取 BudgetSummary 当前值。
     * 提供只读访问入口，避免调用方直接依赖内部实现细节。
     * 返回值可被上层流程直接消费或继续加工。
     * @return 当前步骤处理后的返回结果。
     */
    public String getBudgetSummary() {
        return budgetSummary;
    }

    /**
     * 设置 BudgetSummary 的值。
     * 统一写入路径，便于后续扩展校验、审计或联动逻辑。
     * 对空值输入会执行安全兜底，避免状态污染。
     * @param budgetSummary 输入参数 budgetSummary。
     */
    public void setBudgetSummary(String budgetSummary) {
        this.budgetSummary = budgetSummary == null ? "" : budgetSummary;
    }

    /**
     * 获取 TipsSummary 当前值。
     * 提供只读访问入口，避免调用方直接依赖内部实现细节。
     * 返回值可被上层流程直接消费或继续加工。
     * @return 当前步骤处理后的返回结果。
     */
    public String getTipsSummary() {
        return tipsSummary;
    }

    /**
     * 设置 TipsSummary 的值。
     * 统一写入路径，便于后续扩展校验、审计或联动逻辑。
     * 对空值输入会执行安全兜底，避免状态污染。
     * @param tipsSummary 输入参数 tipsSummary。
     */
    public void setTipsSummary(String tipsSummary) {
        this.tipsSummary = tipsSummary == null ? "" : tipsSummary;
    }

    /**
     * 获取 WeatherSummary 当前值。
     * 提供只读访问入口，避免调用方直接依赖内部实现细节。
     * 返回值可被上层流程直接消费或继续加工。
     * @return 当前步骤处理后的返回结果。
     */
    public String getWeatherSummary() {
        return weatherSummary;
    }

    /**
     * 设置 WeatherSummary 的值。
     * 统一写入路径，便于后续扩展校验、审计或联动逻辑。
     * 对空值输入会执行安全兜底，避免状态污染。
     * @param weatherSummary 输入参数 weatherSummary。
     */
    public void setWeatherSummary(String weatherSummary) {
        this.weatherSummary = weatherSummary == null ? "" : weatherSummary;
    }

    /**
     * 获取 PlanScoreSummary 当前值。
     * 提供只读访问入口，避免调用方直接依赖内部实现细节。
     * 返回值可被上层流程直接消费或继续加工。
     * @return 当前步骤处理后的返回结果。
     */
    public String getPlanScoreSummary() {
        return planScoreSummary;
    }

    /**
     * 设置 PlanScoreSummary 的值。
     * 统一写入路径，便于后续扩展校验、审计或联动逻辑。
     * 对空值输入会执行安全兜底，避免状态污染。
     * @param planScoreSummary 输入参数 planScoreSummary。
     */
    public void setPlanScoreSummary(String planScoreSummary) {
        this.planScoreSummary = planScoreSummary == null ? "" : planScoreSummary;
    }

    /**
     * 获取 HighRiskSummary 当前值。
     * 提供只读访问入口，避免调用方直接依赖内部实现细节。
     * 返回值可被上层流程直接消费或继续加工。
     * @return 当前步骤处理后的返回结果。
     */
    public String getHighRiskSummary() {
        return highRiskSummary;
    }

    /**
     * 设置 HighRiskSummary 的值。
     * 统一写入路径，便于后续扩展校验、审计或联动逻辑。
     * 对空值输入会执行安全兜底，避免状态污染。
     * @param highRiskSummary 输入参数 highRiskSummary。
     */
    public void setHighRiskSummary(String highRiskSummary) {
        this.highRiskSummary = highRiskSummary == null ? "" : highRiskSummary;
    }

    /**
     * 获取 BlockedTools 当前值。
     * 提供只读访问入口，避免调用方直接依赖内部实现细节。
     * 返回值可被上层流程直接消费或继续加工。
     * @return 当前步骤处理后的返回结果。
     */
    public List<String> getBlockedTools() {
        return blockedTools;
    }

    /**
     * 设置 BlockedTools 的值。
     * 统一写入路径，便于后续扩展校验、审计或联动逻辑。
     * 对空值输入会执行安全兜底，避免状态污染。
     * @param blockedTools 输入参数 blockedTools。
     */
    public void setBlockedTools(List<String> blockedTools) {
        this.blockedTools = blockedTools == null ? List.of() : List.copyOf(blockedTools);
    }

    /**
     * 获取 GateReason 当前值。
     * 提供只读访问入口，避免调用方直接依赖内部实现细节。
     * 返回值可被上层流程直接消费或继续加工。
     * @return 当前步骤处理后的返回结果。
     */
    public String getGateReason() {
        return gateReason;
    }

    /**
     * 设置 GateReason 的值。
     * 统一写入路径，便于后续扩展校验、审计或联动逻辑。
     * 对空值输入会执行安全兜底，避免状态污染。
     * @param gateReason 输入参数 gateReason。
     */
    public void setGateReason(String gateReason) {
        this.gateReason = gateReason == null ? "" : gateReason;
    }

    /**
     * 执行 isHighRiskChecked 条件判断。
     * 用于控制流程分支或开关策略，保证行为可预测。
     * 判断逻辑应保持轻量，避免引入额外副作用。
     * @return 判断结果：`true` 表示满足条件，`false` 表示不满足条件。
     */
    public boolean isHighRiskChecked() {
        return highRiskChecked;
    }

    /**
     * 设置 HighRiskChecked 的值。
     * 统一写入路径，便于后续扩展校验、审计或联动逻辑。
     * 对空值输入会执行安全兜底，避免状态污染。
     * @param highRiskChecked 输入参数 highRiskChecked。
     */
    public void setHighRiskChecked(boolean highRiskChecked) {
        this.highRiskChecked = highRiskChecked;
    }

    /**
     * 执行 clip 处理逻辑。
     * 负责当前步骤的核心处理，确保输入被稳定转换为可复用输出。
     * 处理过程中会遵循当前工具的权限、限流和容错约束。
     * @param text 输入参数 text。
     * @param maxLength 输入参数 maxLength。
     * @return 当前步骤处理后的返回结果。
     */
    private String clip(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String normalized = text.trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }
}
