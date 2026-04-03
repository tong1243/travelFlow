package com.example.demo.rag.repo;

import com.example.demo.rag.entity.ConversationMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, Long> {

    List<ConversationMessage> findTop20BySessionIdOrderByCreatedAtDesc(String sessionId);

    List<ConversationMessage> findBySessionIdOrderByCreatedAtAsc(String sessionId);
}
