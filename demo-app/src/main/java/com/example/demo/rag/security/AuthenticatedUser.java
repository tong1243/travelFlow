package com.example.demo.rag.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * AuthenticatedUser类。
 * 该类型负责认证授权与访问控制，保障系统安全边界。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 */
public class AuthenticatedUser implements UserDetails {

    private final Long id;
    private final String username;
    private final String password;
    private final String role;
    private final boolean enabled;

    /**
     * 构造并初始化 AuthenticatedUser 对象。
     * 该构造方法用于注入运行所需依赖，保证对象创建后即可参与完整流程。
     * 该方法用于认证授权处理，确保访问链路符合安全策略。
     * @param id 输入参数 id，用于参与本次处理流程。
     * @param username 输入参数 username，用于参与本次处理流程。
     * @param password 输入参数 password，用于参与本次处理流程。
     * @param role 输入参数 role，用于参与本次处理流程。
     * @param enabled 输入参数 enabled，用于参与本次处理流程。
     */
    public AuthenticatedUser(Long id, String username, String password, String role, boolean enabled) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.role = role;
        this.enabled = enabled;
    }

    /**
     * 获取 Id 字段值。
     * 该方法提供只读访问入口，减少调用方直接操作内部状态的风险。
     * 该方法用于认证授权处理，确保访问链路符合安全策略。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public Long getId() {
        return id;
    }

    /**
     * 获取 Role 字段值。
     * 该方法提供只读访问入口，减少调用方直接操作内部状态的风险。
     * 该方法用于认证授权处理，确保访问链路符合安全策略。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public String getRole() {
        return role;
    }

    @Override
    /**
     * 获取 Authorities 字段值。
     * 该方法提供只读访问入口，减少调用方直接操作内部状态的风险。
     * 该方法用于认证授权处理，确保访问链路符合安全策略。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public Collection<? extends GrantedAuthority> getAuthorities() {
        String roleName = role == null ? "USER" : role.trim().toUpperCase();
        if (!roleName.startsWith("ROLE_")) {
            roleName = "ROLE_" + roleName;
        }
        return List.of(new SimpleGrantedAuthority(roleName));
    }

    @Override
    /**
     * 获取 Password 字段值。
     * 该方法提供只读访问入口，减少调用方直接操作内部状态的风险。
     * 该方法用于认证授权处理，确保访问链路符合安全策略。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public String getPassword() {
        return password;
    }

    @Override
    /**
     * 获取 Username 字段值。
     * 该方法提供只读访问入口，减少调用方直接操作内部状态的风险。
     * 该方法用于认证授权处理，确保访问链路符合安全策略。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public String getUsername() {
        return username;
    }

    @Override
    /**
     * 执行 isAccountNonExpired 条件判断。
     * 该方法返回布尔判定结果，用于上层流程分支控制和策略选择。
     * 该方法用于认证授权处理，确保访问链路符合安全策略。
     * @return 返回判断结果：`true` 表示条件成立，`false` 表示条件不成立。
     */
    public boolean isAccountNonExpired() {
        return enabled;
    }

    @Override
    /**
     * 执行 isAccountNonLocked 条件判断。
     * 该方法返回布尔判定结果，用于上层流程分支控制和策略选择。
     * 该方法用于认证授权处理，确保访问链路符合安全策略。
     * @return 返回判断结果：`true` 表示条件成立，`false` 表示条件不成立。
     */
    public boolean isAccountNonLocked() {
        return enabled;
    }

    @Override
    /**
     * 执行 isCredentialsNonExpired 条件判断。
     * 该方法返回布尔判定结果，用于上层流程分支控制和策略选择。
     * 该方法用于认证授权处理，确保访问链路符合安全策略。
     * @return 返回判断结果：`true` 表示条件成立，`false` 表示条件不成立。
     */
    public boolean isCredentialsNonExpired() {
        return enabled;
    }

    @Override
    /**
     * 执行 isEnabled 条件判断。
     * 该方法返回布尔判定结果，用于上层流程分支控制和策略选择。
     * 该方法用于认证授权处理，确保访问链路符合安全策略。
     * @return 返回判断结果：`true` 表示条件成立，`false` 表示条件不成立。
     */
    public boolean isEnabled() {
        return enabled;
    }
}
