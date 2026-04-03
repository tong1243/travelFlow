package com.example.demo.rag.controller;

import com.example.demo.rag.dto.AgentChatRequest;
import com.example.demo.rag.dto.AgentChatResponse;
import com.example.demo.rag.security.AuthenticatedUser;
import com.example.demo.rag.service.RagAgentService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat/agent")
public class AgentChatController {

    private final RagAgentService ragAgentService;

    public AgentChatController(RagAgentService ragAgentService) {
        this.ragAgentService = ragAgentService;
    }

    @PostMapping("/ask")
    public AgentChatResponse ask(@AuthenticationPrincipal AuthenticatedUser user,
                                 @Valid @RequestBody AgentChatRequest request) {
        return ragAgentService.chat(user.getId(), request);
    }
}
