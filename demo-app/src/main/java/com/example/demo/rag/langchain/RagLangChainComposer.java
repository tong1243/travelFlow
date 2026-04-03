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
            You are an AI travel assistant.
            - Give practical and safe travel guidance.
            - Prefer information from provided knowledge context.
            - If context is insufficient, explicitly say what is uncertain.
            - Keep answer structured with clear actionable suggestions.
            - Use concise section titles in Markdown.
            """;

    private static final PromptTemplate KNOWLEDGE_TEMPLATE = PromptTemplate.from("""
            Knowledge context from retrieval:
            {{knowledge_context}}

            Response policy:
            1) Cite sources by reference index like [1], [2].
            2) If evidence is weak, explicitly flag uncertainty.
            3) Do not fabricate unavailable details.
            """);

    public List<Map<String, String>> composeMessages(List<ConversationService.ContextMessage> history,
                                                     List<RagReferenceItem> references,
                                                     String ragContextText) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(message("system", SYSTEM_PROMPT));

        if (ragContextText != null && !ragContextText.isBlank()) {
            // LangChain template keeps context composition centralized and reusable.
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
            messages.add(message("system", "No external reference found for this turn."));
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
