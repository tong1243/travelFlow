package com.example.demo.rag.service.agent;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(15)
/**
 * FlightQueryTool 类。
 * 作为智能体工具节点参与编排流程，负责触发判断、执行与轨迹写入。
 * 通过与 AgentToolRuntime 协作，将中间结果沉淀为后续推理可消费的数据。
 */
public class FlightQueryTool implements AgentTool {

    private final FlightLookupService flightLookupService;

    /**
     * 构造并初始化 FlightQueryTool 对象。
     * 通过依赖注入完成必需组件装配，确保实例创建后即可参与完整流程。
     * 初始化阶段不会触发业务副作用，仅完成运行准备。
     * @param flightLookupService 输入参数 flightLookupService。
     */
    public FlightQueryTool(FlightLookupService flightLookupService) {
        this.flightLookupService = flightLookupService;
    }

    @Override
    /**
     * 返回工具在编排链路中的名称。
     * 该名称会写入轨迹与日志，用于排障、评估和前端可视化展示。
     * 建议保持语义稳定，避免影响已有监控和调用方解析。
     * @return 工具名称字符串，用于链路追踪与前端展示。
     */
    public String toolName() {
        return "机票查询";
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
        if (context == null || context.request() == null) {
            return true;
        }
        String travelMode = context.request().travelMode();
        if (travelMode == null || travelMode.isBlank()) {
            return true;
        }
        return "飞机".equals(travelMode.trim());
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
        FlightLookupService.FlightLookupResult result = flightLookupService.lookupFlights(runtime.getQuestion());
        runtime.addTrace(toolName(), "mode=flight_lookup; parse=origin,destination,date", result.summary());
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
}
