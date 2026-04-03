package com.example.demo.rag.repo;

import com.example.demo.rag.entity.ConversationSession;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ConversationSessionRepository extends JpaRepository<ConversationSession, String> {

    List<ConversationSession> findByUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<ConversationSession> findByIdAndUserId(String id, Long userId);

    @Modifying
    @Query("update ConversationSession s set s.updatedAt = :updatedAt where s.id = :sessionId")
    void touchSession(@Param("sessionId") String sessionId, @Param("updatedAt") Instant updatedAt);
}
