package com.example.demo.rag.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "conversation_messages")
/**
 * ConversationMessage类。
 * 该类型负责描述持久化结构，并承载实体生命周期相关逻辑。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 */
public class ConversationMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(nullable = false, length = 16)
    private String role;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    /**
     * 执行 prePersist 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法用于维护实体状态，保证持久化对象的一致性与可追踪性。
     */
    public void prePersist() {
        this.createdAt = Instant.now();
    }

    /**
     * 获取 Id 字段值。
     * 该方法提供只读访问入口，减少调用方直接操作内部状态的风险。
     * 该方法用于维护实体状态，保证持久化对象的一致性与可追踪性。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public Long getId() {
        return id;
    }

    /**
     * 获取 SessionId 字段值。
     * 该方法提供只读访问入口，减少调用方直接操作内部状态的风险。
     * 该方法用于维护实体状态，保证持久化对象的一致性与可追踪性。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * 设置 SessionId 字段值。
     * 该方法统一字段写入入口，便于后续扩展校验、审计或联动逻辑。
     * 该方法用于维护实体状态，保证持久化对象的一致性与可追踪性。
     * @param sessionId 输入参数 sessionId，用于参与本次处理流程。
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * 获取 Role 字段值。
     * 该方法提供只读访问入口，减少调用方直接操作内部状态的风险。
     * 该方法用于维护实体状态，保证持久化对象的一致性与可追踪性。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public String getRole() {
        return role;
    }

    /**
     * 设置 Role 字段值。
     * 该方法统一字段写入入口，便于后续扩展校验、审计或联动逻辑。
     * 该方法用于维护实体状态，保证持久化对象的一致性与可追踪性。
     * @param role 输入参数 role，用于参与本次处理流程。
     */
    public void setRole(String role) {
        this.role = role;
    }

    /**
     * 获取 Content 字段值。
     * 该方法提供只读访问入口，减少调用方直接操作内部状态的风险。
     * 该方法用于维护实体状态，保证持久化对象的一致性与可追踪性。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public String getContent() {
        return content;
    }

    /**
     * 设置 Content 字段值。
     * 该方法统一字段写入入口，便于后续扩展校验、审计或联动逻辑。
     * 该方法用于维护实体状态，保证持久化对象的一致性与可追踪性。
     * @param content 输入参数 content，用于参与本次处理流程。
     */
    public void setContent(String content) {
        this.content = content;
    }

    /**
     * 获取 CreatedAt 字段值。
     * 该方法提供只读访问入口，减少调用方直接操作内部状态的风险。
     * 该方法用于维护实体状态，保证持久化对象的一致性与可追踪性。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public Instant getCreatedAt() {
        return createdAt;
    }
}
