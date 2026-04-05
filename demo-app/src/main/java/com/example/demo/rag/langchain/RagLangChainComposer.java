package com.example.demo.rag.langchain;

import com.example.demo.rag.dto.RagReferenceItem;
import com.example.demo.rag.service.ConversationService;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RagLangChainComposer {

    private static final String SYSTEM_PROMPT = """
            你是一个旅游规划助手。
            - 优先基于提供的知识上下文回答，避免编造事实。
            - 结论要可执行，优先给出明确步骤和建议。
            - 若证据不足，请明确指出不确定点。
            - 输出保持结构化，使用简洁的 Markdown 小标题。
            - 默认使用中文回答。
            """;

    private static final PromptTemplate KNOWLEDGE_TEMPLATE = PromptTemplate.from("""
            以下是检索得到的知识上下文：
            {{knowledge_context}}

            回答规则：
            1) 引用信息时用 [1] [2] 这类编号标注。
            2) 如果证据较弱，必须显式提示不确定性。
            3) 不要补造上下文中不存在的细节。
            """);

    public List<Map<String, String>> composeMessages(List<ConversationService.ContextMessage> history,
                                                     List<RagReferenceItem> references,
                                                     String ragContextText) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(message("system", SYSTEM_PROMPT));

        if (ragContextText != null && !ragContextText.isBlank()) {
            Prompt prompt = KNOWLEDGE_TEMPLATE.apply(Map.of("knowledge_context", ragContextText));
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

    private Map<String, String> message(String role, String content) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("role", role);
        item.put("content", content);
        return item;
    }
}

