package com.example.demo.rag.repo;

import com.example.demo.rag.entity.ConversationSession;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * ConversationSessionRepository接口。
 * 该类型负责声明数据访问能力，由 Spring Data 生成具体实现。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 */
public interface ConversationSessionRepository extends JpaRepository<ConversationSession, String> {

    /**
     * 执行 findByUserIdOrderByUpdatedAtDesc 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法用于定义仓储查询语义，执行逻辑由框架按命名规则生成。
     * @param userId 输入参数 userId，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    List<ConversationSession> findByUserIdOrderByUpdatedAtDesc(Long userId);

    /**
     * 执行 findByIdAndUserId 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法用于定义仓储查询语义，执行逻辑由框架按命名规则生成。
     * @param id 输入参数 id，用于参与本次处理流程。
     * @param userId 输入参数 userId，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    Optional<ConversationSession> findByIdAndUserId(String id, Long userId);

    @Modifying
    @Query("update ConversationSession s set s.updatedAt = :updatedAt where s.id = :sessionId")
    /**
     * 执行 touchSession 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法用于定义仓储查询语义，执行逻辑由框架按命名规则生成。
     * @param sessionId 输入参数 sessionId，用于参与本次处理流程。
     * @param updatedAt 输入参数 updatedAt，用于参与本次处理流程。
     */
    void touchSession(@Param("sessionId") String sessionId, @Param("updatedAt") Instant updatedAt);
}
