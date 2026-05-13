package com.example.demo.rag.service.agent;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(30)
/**
 * TravelTipsTool 类。
 * 作为智能体工具节点参与编排流程，负责触发判断、执行与轨迹写入。
 * 通过与 AgentToolRuntime 协作，将中间结果沉淀为后续推理可消费的数据。
 */
public class TravelTipsTool implements AgentTool {

    @Override
    /**
     * 返回工具在编排链路中的名称。
     * 该名称会写入轨迹与日志，用于排障、评估和前端可视化展示。
     * 建议保持语义稳定，避免影响已有监控和调用方解析。
     * @return 工具名称字符串，用于链路追踪与前端展示。
     */
    public String toolName() {
        return "出行提示";
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
        String summary = buildTravelTips();
        runtime.setTipsSummary(summary);
        runtime.addTrace(toolName(), "mode=travel_tips; based_on=budget,weather,transport", summary);
    }

    /**
     * 构建 buildTravelTips 所需的静态映射或聚合结构。
     * 将常用城市、站点或关键词进行集中维护，便于统一复用。
     * 构建结果通常为不可变集合，避免运行时被意外修改。
     * @return 构建完成的映射或集合对象。
     */
    private String buildTravelTips() {
        return String.join("\n",
                "出行提示：",
                "1) 证件和票据分开放置，并保留电子备份。",
                "2) 每天预留 10% 机动时间，避免转场过紧。",
                "3) 住宿优先选择交通便利区域，降低通勤损耗。",
                "4) 热门景点尽量错峰预约，减少排队时间。",
                "5) 预算建议按 70% 固定 + 20% 弹性 + 10% 应急分配。"
        );
    }
}
