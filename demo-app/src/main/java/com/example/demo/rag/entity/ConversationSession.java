package com.example.demo.rag.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "conversation_sessions")
/**
 * ConversationSession类。
 * 该类型负责描述持久化结构，并承载实体生命周期相关逻辑。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 */
public class ConversationSession {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(length = 255)
    private String title;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    /**
     * 执行 prePersist 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法用于维护实体状态，保证持久化对象的一致性与可追踪性。
     */
    public void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    /**
     * 执行 preUpdate 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法用于维护实体状态，保证持久化对象的一致性与可追踪性。
     */
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * 获取 Id 字段值。
     * 该方法提供只读访问入口，减少调用方直接操作内部状态的风险。
     * 该方法用于维护实体状态，保证持久化对象的一致性与可追踪性。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public String getId() {
        return id;
    }

    /**
     * 设置 Id 字段值。
     * 该方法统一字段写入入口，便于后续扩展校验、审计或联动逻辑。
     * 该方法用于维护实体状态，保证持久化对象的一致性与可追踪性。
     * @param id 输入参数 id，用于参与本次处理流程。
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * 获取 UserId 字段值。
     * 该方法提供只读访问入口，减少调用方直接操作内部状态的风险。
     * 该方法用于维护实体状态，保证持久化对象的一致性与可追踪性。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * 设置 UserId 字段值。
     * 该方法统一字段写入入口，便于后续扩展校验、审计或联动逻辑。
     * 该方法用于维护实体状态，保证持久化对象的一致性与可追踪性。
     * @param userId 输入参数 userId，用于参与本次处理流程。
     */
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    /**
     * 获取 Title 字段值。
     * 该方法提供只读访问入口，减少调用方直接操作内部状态的风险。
     * 该方法用于维护实体状态，保证持久化对象的一致性与可追踪性。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public String getTitle() {
        return title;
    }

    /**
     * 设置 Title 字段值。
     * 该方法统一字段写入入口，便于后续扩展校验、审计或联动逻辑。
     * 该方法用于维护实体状态，保证持久化对象的一致性与可追踪性。
     * @param title 输入参数 title，用于参与本次处理流程。
     */
    public void setTitle(String title) {
        this.title = title;
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

    /**
     * 获取 UpdatedAt 字段值。
     * 该方法提供只读访问入口，减少调用方直接操作内部状态的风险。
     * 该方法用于维护实体状态，保证持久化对象的一致性与可追踪性。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
