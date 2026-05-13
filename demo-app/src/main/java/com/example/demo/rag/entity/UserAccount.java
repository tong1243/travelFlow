package com.example.demo.rag.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "users")
/**
 * UserAccount类。
 * 该类型负责描述持久化结构，并承载实体生命周期相关逻辑。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 */
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(unique = true, length = 128)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 32)
    private String role = "USER";

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

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
     * 获取 Username 字段值。
     * 该方法提供只读访问入口，减少调用方直接操作内部状态的风险。
     * 该方法用于维护实体状态，保证持久化对象的一致性与可追踪性。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public String getUsername() {
        return username;
    }

    /**
     * 设置 Username 字段值。
     * 该方法统一字段写入入口，便于后续扩展校验、审计或联动逻辑。
     * 该方法用于维护实体状态，保证持久化对象的一致性与可追踪性。
     * @param username 输入参数 username，用于参与本次处理流程。
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * 获取 Email 字段值。
     * 该方法提供只读访问入口，减少调用方直接操作内部状态的风险。
     * 该方法用于维护实体状态，保证持久化对象的一致性与可追踪性。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public String getEmail() {
        return email;
    }

    /**
     * 设置 Email 字段值。
     * 该方法统一字段写入入口，便于后续扩展校验、审计或联动逻辑。
     * 该方法用于维护实体状态，保证持久化对象的一致性与可追踪性。
     * @param email 输入参数 email，用于参与本次处理流程。
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * 获取 PasswordHash 字段值。
     * 该方法提供只读访问入口，减少调用方直接操作内部状态的风险。
     * 该方法用于维护实体状态，保证持久化对象的一致性与可追踪性。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public String getPasswordHash() {
        return passwordHash;
    }

    /**
     * 设置 PasswordHash 字段值。
     * 该方法统一字段写入入口，便于后续扩展校验、审计或联动逻辑。
     * 该方法用于维护实体状态，保证持久化对象的一致性与可追踪性。
     * @param passwordHash 输入参数 passwordHash，用于参与本次处理流程。
     */
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
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
     * 执行 isEnabled 条件判断。
     * 该方法返回布尔判定结果，用于上层流程分支控制和策略选择。
     * 该方法用于维护实体状态，保证持久化对象的一致性与可追踪性。
     * @return 返回判断结果：`true` 表示条件成立，`false` 表示条件不成立。
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置 Enabled 字段值。
     * 该方法统一字段写入入口，便于后续扩展校验、审计或联动逻辑。
     * 该方法用于维护实体状态，保证持久化对象的一致性与可追踪性。
     * @param enabled 输入参数 enabled，用于参与本次处理流程。
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
