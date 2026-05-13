package com.example.demo.rag.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "kb_documents")
/**
 * KnowledgeDocument类。
 * 该类型负责描述持久化结构，并承载实体生命周期相关逻辑。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 */
public class KnowledgeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "source_type", length = 64)
    private String sourceType;

    @Column(name = "source_ref", length = 255)
    private String sourceRef;

    @Column(nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(name = "version_no", nullable = false)
    private int versionNo = 1;

    @Column(name = "created_by")
    private Long createdBy;

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
    public Long getId() {
        return id;
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
     * 获取 SourceType 字段值。
     * 该方法提供只读访问入口，减少调用方直接操作内部状态的风险。
     * 该方法用于维护实体状态，保证持久化对象的一致性与可追踪性。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public String getSourceType() {
        return sourceType;
    }

    /**
     * 设置 SourceType 字段值。
     * 该方法统一字段写入入口，便于后续扩展校验、审计或联动逻辑。
     * 该方法用于维护实体状态，保证持久化对象的一致性与可追踪性。
     * @param sourceType 输入参数 sourceType，用于参与本次处理流程。
     */
    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    /**
     * 获取 SourceRef 字段值。
     * 该方法提供只读访问入口，减少调用方直接操作内部状态的风险。
     * 该方法用于维护实体状态，保证持久化对象的一致性与可追踪性。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public String getSourceRef() {
        return sourceRef;
    }

    /**
     * 设置 SourceRef 字段值。
     * 该方法统一字段写入入口，便于后续扩展校验、审计或联动逻辑。
     * 该方法用于维护实体状态，保证持久化对象的一致性与可追踪性。
     * @param sourceRef 输入参数 sourceRef，用于参与本次处理流程。
     */
    public void setSourceRef(String sourceRef) {
        this.sourceRef = sourceRef;
    }

    /**
     * 获取 Status 字段值。
     * 该方法提供只读访问入口，减少调用方直接操作内部状态的风险。
     * 该方法用于维护实体状态，保证持久化对象的一致性与可追踪性。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public String getStatus() {
        return status;
    }

    /**
     * 设置 Status 字段值。
     * 该方法统一字段写入入口，便于后续扩展校验、审计或联动逻辑。
     * 该方法用于维护实体状态，保证持久化对象的一致性与可追踪性。
     * @param status 输入参数 status，用于参与本次处理流程。
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * 获取 VersionNo 字段值。
     * 该方法提供只读访问入口，减少调用方直接操作内部状态的风险。
     * 该方法用于维护实体状态，保证持久化对象的一致性与可追踪性。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public int getVersionNo() {
        return versionNo;
    }

    /**
     * 设置 VersionNo 字段值。
     * 该方法统一字段写入入口，便于后续扩展校验、审计或联动逻辑。
     * 该方法用于维护实体状态，保证持久化对象的一致性与可追踪性。
     * @param versionNo 输入参数 versionNo，用于参与本次处理流程。
     */
    public void setVersionNo(int versionNo) {
        this.versionNo = versionNo;
    }

    /**
     * 获取 CreatedBy 字段值。
     * 该方法提供只读访问入口，减少调用方直接操作内部状态的风险。
     * 该方法用于维护实体状态，保证持久化对象的一致性与可追踪性。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public Long getCreatedBy() {
        return createdBy;
    }

    /**
     * 设置 CreatedBy 字段值。
     * 该方法统一字段写入入口，便于后续扩展校验、审计或联动逻辑。
     * 该方法用于维护实体状态，保证持久化对象的一致性与可追踪性。
     * @param createdBy 输入参数 createdBy，用于参与本次处理流程。
     */
    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
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
