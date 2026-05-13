package com.example.demo.rag.controller;

import com.example.demo.rag.dto.ConversationMessageResponse;
import com.example.demo.rag.dto.ConversationSummaryResponse;
import com.example.demo.rag.security.AuthenticatedUser;
import com.example.demo.rag.service.ConversationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ConversationService conversationService;

    public ChatController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping("/sessions")
    @Deprecated(since = "2026-04", forRemoval = false)
    public List<ConversationSummaryResponse> sessions(@AuthenticationPrincipal AuthenticatedUser user) {
        return conversationService.listSessions(user.getId());
    }

    @GetMapping("/sessions/{sessionId}/messages")
    @Deprecated(since = "2026-04", forRemoval = false)
    public List<ConversationMessageResponse> sessionMessages(@AuthenticationPrincipal AuthenticatedUser user,
                                                             @PathVariable("sessionId") String sessionId) {
        return conversationService.listSessionHistory(user.getId(), sessionId);
    }
}