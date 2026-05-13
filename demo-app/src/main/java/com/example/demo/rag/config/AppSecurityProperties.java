package com.example.demo.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
/**
 * AppSecurityProperties类。
 * 该类型负责定义模块配置项和基础 Bean 装配，影响运行时行为。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 */
public class AppSecurityProperties {

    private String jwtSecret;
    private long jwtExpireSeconds = 86_400;

    /**
     * 获取JwtSecret 的当前值。
     * 该方法用于对外暴露只读访问能力，避免调用方直接操作内部字段。
     * 该方法用于配置注入与运行参数管理，直接影响模块初始化和运行行为。
     * @return 返回该步骤的处理结果；若无有效结果，按实现约定返回空值或默认值。
     */
    public String getJwtSecret() {
        return jwtSecret;
    }

    /**
     * 设置 JwtSecret 字段值。
     * 该方法统一字段写入入口，便于后续扩展校验、审计或联动逻辑。
     * 该方法用于配置管理与 Bean 装配，直接影响模块运行效果。
     * @param jwtSecret 输入参数 jwtSecret，用于参与本次处理流程。
     */
    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    /**
     * 获取JwtExpireSeconds 的当前值。
     * 该方法用于对外暴露只读访问能力，避免调用方直接操作内部字段。
     * 该方法用于配置注入与运行参数管理，直接影响模块初始化和运行行为。
     * @return 返回该步骤的处理结果；若无有效结果，按实现约定返回空值或默认值。
     */
    public long getJwtExpireSeconds() {
        return jwtExpireSeconds;
    }

    /**
     * 设置 JwtExpireSeconds 字段值。
     * 该方法统一字段写入入口，便于后续扩展校验、审计或联动逻辑。
     * 该方法用于配置管理与 Bean 装配，直接影响模块运行效果。
     * @param jwtExpireSeconds 输入参数 jwtExpireSeconds，用于参与本次处理流程。
     */
    public void setJwtExpireSeconds(long jwtExpireSeconds) {
        this.jwtExpireSeconds = jwtExpireSeconds;
    }
}
