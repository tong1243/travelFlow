package com.example.demo.rag.langchain;

import com.example.demo.assistant.PromptFileService;
import com.example.demo.rag.dto.RagReferenceItem;
import com.example.demo.rag.service.ConversationService;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.IllegalFormatException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
/**
 * RagLangChainComposer类。
 * 该类型负责组装提示词与上下文内容，提升模型输出的稳定性与可解释性。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 */
public class RagLangChainComposer {

    private static final String DEFAULT_MAIN_PROMPT_TEMPLATE = """
            你是一个旅游规划助手。
            当前服务器时间：%s（时区：%s）
            - 优先基于提供的知识上下文回答，避免编造事实。
            - 结论要可执行，优先给出明确步骤和建议。
            - 若证据不足，请明确指出不确定点。
            - 输出保持结构化，使用简洁的分级小标题。
            - 默认使用中文回答。
            """;
    private static final DateTimeFormatter PROMPT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX");

    private static final String DEFAULT_RAG_SUMMARIZE_TEMPLATE = """
            以下是检索得到的知识上下文：
            {{knowledge_context}}

            回答规则：
            1) 不要输出 [1]、[2]、[1][2] 这类中括号引用标记。
            2) 如果证据较弱，必须显式提示不确定性。
            3) 不要补造上下文中不存在的细节。
            """;

    private final PromptFileService promptFileService;

    public RagLangChainComposer(PromptFileService promptFileService) {
        this.promptFileService = promptFileService;
    }

    /**
     * 执行 composeMessages 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法遵循当前模块约定，承担明确的输入处理与结果输出职责。
     * @param history 输入参数 history，用于参与本次处理流程。
     * @param references 输入参数 references，用于参与本次处理流程。
     * @param ragContextText 输入参数 ragContextText，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public List<Map<String, String>> composeMessages(List<ConversationService.ContextMessage> history,
                                                     List<RagReferenceItem> references,
                                                     String ragContextText) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(message("system", buildSystemPrompt()));

        if (ragContextText != null && !ragContextText.isBlank()) {
            Prompt prompt = buildKnowledgeTemplate().apply(Map.of("knowledge_context", ragContextText));
            messages.add(message("system", prompt.text()));
        }

        if (history != null) {
            for (ConversationService.ContextMessage historyItem : history) {
                if ("assistant".equalsIgnoreCase(historyItem.role())) {
                    messages.add(message("assistant", historyItem.content()));
                } else {
                    messages.add(message("user", historyItem.content()));
                }
            }
        }

        if (references == null || references.isEmpty()) {
            messages.add(message("system", "本轮未检索到外部参考资料，请明确说明不确定性并给出保守建议。"));
        }
        return messages;
    }

    /**
     * 执行 message 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法遵循当前模块约定，承担明确的输入处理与结果输出职责。
     * @param role 输入参数 role，用于参与本次处理流程。
     * @param content 输入参数 content，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    private Map<String, String> message(String role, String content) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("role", role);
        item.put("content", content);
        return item;
    }

    /**
     * 执行 buildSystemPrompt 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法遵循当前模块约定，承担明确的输入处理与结果输出职责。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    private String buildSystemPrompt() {
        ZoneId zone = ZoneId.systemDefault();
        String now = OffsetDateTime.now(zone).format(PROMPT_TIME_FORMATTER);
        String template = promptFileService.loadOrDefault("main_prompt.txt", DEFAULT_MAIN_PROMPT_TEMPLATE);
        return renderPromptWithTime(template, now, zone.getId());
    }

    private PromptTemplate buildKnowledgeTemplate() {
        String template = promptFileService.loadOrDefault("rag_summarize.txt", DEFAULT_RAG_SUMMARIZE_TEMPLATE);
        if (!template.contains("{{knowledge_context}}")) {
            template = template + System.lineSeparator() + System.lineSeparator()
                    + "以下是检索得到的知识上下文：" + System.lineSeparator()
                    + "{{knowledge_context}}";
        }
        return PromptTemplate.from(template);
    }

    private String renderPromptWithTime(String template, String now, String zoneId) {
        String rendered = template
                .replace("{{now}}", now)
                .replace("{{timezone}}", zoneId);
        if (rendered.contains("%s")) {
            try {
                return rendered.formatted(now, zoneId);
            } catch (IllegalFormatException ex) {
                return rendered + System.lineSeparator() + "当前服务器时间：" + now + "（时区：" + zoneId + "）";
            }
        }
        return rendered;
    }
}
