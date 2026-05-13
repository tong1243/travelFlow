package com.example.demo.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app.api-audit")
/**
 * ApiAuditProperties类。
 * 该类型负责定义模块配置项和基础 Bean 装配，影响运行时行为。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 */
public class ApiAuditProperties {

    private boolean enabled = true;
    private boolean includeAllApi = true;
    private List<String> candidatePrefixes = new ArrayList<>(List.of(
            "/api/files/upload",
            "/api/portal/categories",
            "/api/portal/spot-plan",
            "/api/travel/plan",
            "/api/travel/budget",
            "/api/travel/plan/files",
            "/api/travel/file-qa",
            "/api/v1/chat",
            "/api/v1/knowledge",
            "/api/v1/vector",
            "/api/v1/users"
    ));

    /**
     * 判断Enabled 是否满足预期。
     * 该方法返回布尔判定结果，供上层流程进行分支控制或策略选择。
     * 该方法用于配置注入与运行参数管理，直接影响模块初始化和运行行为。
     * @return 返回布尔判断结果，`true` 表示满足条件，`false` 表示不满足条件。
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置 Enabled 字段值。
     * 该方法统一字段写入入口，便于后续扩展校验、审计或联动逻辑。
     * 该方法用于配置管理与 Bean 装配，直接影响模块运行效果。
     * @param enabled 输入参数 enabled，用于参与本次处理流程。
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 判断IncludeAllApi 是否满足预期。
     * 该方法返回布尔判定结果，供上层流程进行分支控制或策略选择。
     * 该方法用于配置注入与运行参数管理，直接影响模块初始化和运行行为。
     * @return 返回布尔判断结果，`true` 表示满足条件，`false` 表示不满足条件。
     */
    public boolean isIncludeAllApi() {
        return includeAllApi;
    }

    /**
     * 设置 IncludeAllApi 字段值。
     * 该方法统一字段写入入口，便于后续扩展校验、审计或联动逻辑。
     * 该方法用于配置管理与 Bean 装配，直接影响模块运行效果。
     * @param includeAllApi 输入参数 includeAllApi，用于参与本次处理流程。
     */
    public void setIncludeAllApi(boolean includeAllApi) {
        this.includeAllApi = includeAllApi;
    }

    /**
     * 获取CandidatePrefixes 的当前值。
     * 该方法用于对外暴露只读访问能力，避免调用方直接操作内部字段。
     * 该方法用于配置注入与运行参数管理，直接影响模块初始化和运行行为。
     * @return 返回该步骤的处理结果；若无有效结果，按实现约定返回空值或默认值。
     */
    public List<String> getCandidatePrefixes() {
        return candidatePrefixes;
    }

    /**
     * 设置 CandidatePrefixes 字段值。
     * 该方法统一字段写入入口，便于后续扩展校验、审计或联动逻辑。
     * 该方法用于配置管理与 Bean 装配，直接影响模块运行效果。
     * @param candidatePrefixes 输入参数 candidatePrefixes，用于参与本次处理流程。
     */
    public void setCandidatePrefixes(List<String> candidatePrefixes) {
        this.candidatePrefixes = candidatePrefixes == null ? new ArrayList<>() : candidatePrefixes;
    }
}
