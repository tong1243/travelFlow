package com.example.demo.rag.service.agent;

import com.example.demo.rag.dto.AgentChatRequest;
import com.example.demo.rag.dto.RagReferenceItem;
import com.example.demo.rag.model.HybridSearchHit;
import com.example.demo.rag.service.HybridRetrievalService;
import com.example.demo.rag.service.KnowledgeBaseService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(10)
/**
 * KnowledgeSearchTool 类。
 * 作为智能体工具节点参与编排流程，负责触发判断、执行与轨迹写入。
 * 通过与 AgentToolRuntime 协作，将中间结果沉淀为后续推理可消费的数据。
 */
public class KnowledgeSearchTool implements AgentTool {

    private final HybridRetrievalService hybridRetrievalService;
    private final KnowledgeBaseService knowledgeBaseService;

    /**
     * 构造并初始化 KnowledgeSearchTool 对象。
     * 通过依赖注入完成必需组件装配，确保实例创建后即可参与完整流程。
     * 初始化阶段不会触发业务副作用，仅完成运行准备。
     * @param hybridRetrievalService 输入参数 hybridRetrievalService。
     * @param knowledgeBaseService 输入参数 knowledgeBaseService。
     */
    public KnowledgeSearchTool(HybridRetrievalService hybridRetrievalService,
                               KnowledgeBaseService knowledgeBaseService) {
        this.hybridRetrievalService = hybridRetrievalService;
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @Override
    /**
     * 返回工具在编排链路中的名称。
     * 该名称会写入轨迹与日志，用于排障、评估和前端可视化展示。
     * 建议保持语义稳定，避免影响已有监控和调用方解析。
     * @return 工具名称字符串，用于链路追踪与前端展示。
     */
    public String toolName() {
        return "知识检索";
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
        AgentChatRequest request = context.request();

        List<HybridSearchHit> hits = hybridRetrievalService.retrieve(
                context.userId(),
                context.isAdmin(),
                runtime.getQuestion(),
                runtime.getTopK(),
                request.sourceType(),
                request.sourceRefContains()
        );

        List<RagReferenceItem> references = knowledgeBaseService.toHybridReferenceItems(hits);
        runtime.setReferences(references);
        runtime.addTrace(
                toolName(),
                "topK=" + runtime.getTopK()
                        + "，sourceType=" + fallback(request.sourceType(), "无")
                        + "，sourceRefContains=" + fallback(request.sourceRefContains(), "无"),
                "检索到 " + references.size() + " 条参考信息"
        );
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
}
