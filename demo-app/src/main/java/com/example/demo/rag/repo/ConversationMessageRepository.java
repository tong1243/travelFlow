package com.example.demo.rag.repo;

import com.example.demo.rag.entity.ConversationMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * ConversationMessageRepository接口。
 * 该类型负责声明数据访问能力，由 Spring Data 生成具体实现。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 */
public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, Long> {

    /**
     * 执行 findTop20BySessionIdOrderByCreatedAtDesc 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法用于定义仓储查询语义，执行逻辑由框架按命名规则生成。
     * @param sessionId 输入参数 sessionId，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    List<ConversationMessage> findTop20BySessionIdOrderByCreatedAtDesc(String sessionId);

    /**
     * 执行 findBySessionIdOrderByCreatedAtAsc 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法用于定义仓储查询语义，执行逻辑由框架按命名规则生成。
     * @param sessionId 输入参数 sessionId，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    List<ConversationMessage> findBySessionIdOrderByCreatedAtAsc(String sessionId);
}
