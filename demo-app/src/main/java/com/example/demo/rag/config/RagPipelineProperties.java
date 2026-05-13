package com.example.demo.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rag")
/**
 * RagPipelineProperties类。
 * 该类型负责定义模块配置项和基础 Bean 装配，影响运行时行为。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 */
public class RagPipelineProperties {

    private int chunkSize = 500;
    private int chunkOverlap = 80;
    private int topK = 1;
    private int contextMaxChars = 4000;
    private int recallTopK = 24;
    private int lexicalPoolSize = 300;
    private double vectorWeight = 0.65;
    private double rerankCoverageWeight = 0.20;
    private int agentMaxSteps = 8;

    /**
     * 获取ChunkSize 的当前值。
     * 该方法用于对外暴露只读访问能力，避免调用方直接操作内部字段。
     * 该方法用于配置注入与运行参数管理，直接影响模块初始化和运行行为。
     * @return 返回该步骤的处理结果；若无有效结果，按实现约定返回空值或默认值。
     */
    public int getChunkSize() {
        return chunkSize;
    }

    /**
     * 设置 ChunkSize 字段值。
     * 该方法统一字段写入入口，便于后续扩展校验、审计或联动逻辑。
     * 该方法用于配置管理与 Bean 装配，直接影响模块运行效果。
     * @param chunkSize 输入参数 chunkSize，用于参与本次处理流程。
     */
    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    /**
     * 获取ChunkOverlap 的当前值。
     * 该方法用于对外暴露只读访问能力，避免调用方直接操作内部字段。
     * 该方法用于配置注入与运行参数管理，直接影响模块初始化和运行行为。
     * @return 返回该步骤的处理结果；若无有效结果，按实现约定返回空值或默认值。
     */
    public int getChunkOverlap() {
        return chunkOverlap;
    }

    /**
     * 设置 ChunkOverlap 字段值。
     * 该方法统一字段写入入口，便于后续扩展校验、审计或联动逻辑。
     * 该方法用于配置管理与 Bean 装配，直接影响模块运行效果。
     * @param chunkOverlap 输入参数 chunkOverlap，用于参与本次处理流程。
     */
    public void setChunkOverlap(int chunkOverlap) {
        this.chunkOverlap = chunkOverlap;
    }

    /**
     * 获取TopK 的当前值。
     * 该方法用于对外暴露只读访问能力，避免调用方直接操作内部字段。
     * 该方法用于配置注入与运行参数管理，直接影响模块初始化和运行行为。
     * @return 返回该步骤的处理结果；若无有效结果，按实现约定返回空值或默认值。
     */
    public int getTopK() {
        return topK;
    }

    /**
     * 设置 TopK 字段值。
     * 该方法统一字段写入入口，便于后续扩展校验、审计或联动逻辑。
     * 该方法用于配置管理与 Bean 装配，直接影响模块运行效果。
     * @param topK 输入参数 topK，用于参与本次处理流程。
     */
    public void setTopK(int topK) {
        this.topK = topK;
    }

    /**
     * 获取ContextMaxChars 的当前值。
     * 该方法用于对外暴露只读访问能力，避免调用方直接操作内部字段。
     * 该方法用于配置注入与运行参数管理，直接影响模块初始化和运行行为。
     * @return 返回该步骤的处理结果；若无有效结果，按实现约定返回空值或默认值。
     */
    public int getContextMaxChars() {
        return contextMaxChars;
    }

    /**
     * 设置 ContextMaxChars 字段值。
     * 该方法统一字段写入入口，便于后续扩展校验、审计或联动逻辑。
     * 该方法用于配置管理与 Bean 装配，直接影响模块运行效果。
     * @param contextMaxChars 输入参数 contextMaxChars，用于参与本次处理流程。
     */
    public void setContextMaxChars(int contextMaxChars) {
        this.contextMaxChars = contextMaxChars;
    }

    /**
     * 获取RecallTopK 的当前值。
     * 该方法用于对外暴露只读访问能力，避免调用方直接操作内部字段。
     * 该方法用于配置注入与运行参数管理，直接影响模块初始化和运行行为。
     * @return 返回该步骤的处理结果；若无有效结果，按实现约定返回空值或默认值。
     */
    public int getRecallTopK() {
        return recallTopK;
    }

    /**
     * 设置 RecallTopK 字段值。
     * 该方法统一字段写入入口，便于后续扩展校验、审计或联动逻辑。
     * 该方法用于配置管理与 Bean 装配，直接影响模块运行效果。
     * @param recallTopK 输入参数 recallTopK，用于参与本次处理流程。
     */
    public void setRecallTopK(int recallTopK) {
        this.recallTopK = recallTopK;
    }

    /**
     * 获取LexicalPoolSize 的当前值。
     * 该方法用于对外暴露只读访问能力，避免调用方直接操作内部字段。
     * 该方法用于配置注入与运行参数管理，直接影响模块初始化和运行行为。
     * @return 返回该步骤的处理结果；若无有效结果，按实现约定返回空值或默认值。
     */
    public int getLexicalPoolSize() {
        return lexicalPoolSize;
    }

    /**
     * 设置 LexicalPoolSize 字段值。
     * 该方法统一字段写入入口，便于后续扩展校验、审计或联动逻辑。
     * 该方法用于配置管理与 Bean 装配，直接影响模块运行效果。
     * @param lexicalPoolSize 输入参数 lexicalPoolSize，用于参与本次处理流程。
     */
    public void setLexicalPoolSize(int lexicalPoolSize) {
        this.lexicalPoolSize = lexicalPoolSize;
    }

    /**
     * 获取VectorWeight 的当前值。
     * 该方法用于对外暴露只读访问能力，避免调用方直接操作内部字段。
     * 该方法用于配置注入与运行参数管理，直接影响模块初始化和运行行为。
     * @return 返回该步骤的处理结果；若无有效结果，按实现约定返回空值或默认值。
     */
    public double getVectorWeight() {
        return vectorWeight;
    }

    /**
     * 设置 VectorWeight 字段值。
     * 该方法统一字段写入入口，便于后续扩展校验、审计或联动逻辑。
     * 该方法用于配置管理与 Bean 装配，直接影响模块运行效果。
     * @param vectorWeight 输入参数 vectorWeight，用于参与本次处理流程。
     */
    public void setVectorWeight(double vectorWeight) {
        this.vectorWeight = vectorWeight;
    }

    /**
     * 获取RerankCoverageWeight 的当前值。
     * 该方法用于对外暴露只读访问能力，避免调用方直接操作内部字段。
     * 该方法用于配置注入与运行参数管理，直接影响模块初始化和运行行为。
     * @return 返回该步骤的处理结果；若无有效结果，按实现约定返回空值或默认值。
     */
    public double getRerankCoverageWeight() {
        return rerankCoverageWeight;
    }

    /**
     * 设置 RerankCoverageWeight 字段值。
     * 该方法统一字段写入入口，便于后续扩展校验、审计或联动逻辑。
     * 该方法用于配置管理与 Bean 装配，直接影响模块运行效果。
     * @param rerankCoverageWeight 输入参数 rerankCoverageWeight，用于参与本次处理流程。
     */
    public void setRerankCoverageWeight(double rerankCoverageWeight) {
        this.rerankCoverageWeight = rerankCoverageWeight;
    }

    /**
     * 获取AgentMaxSteps 的当前值。
     * 该方法用于对外暴露只读访问能力，避免调用方直接操作内部字段。
     * 该方法用于配置注入与运行参数管理，直接影响模块初始化和运行行为。
     * @return 返回该步骤的处理结果；若无有效结果，按实现约定返回空值或默认值。
     */
    public int getAgentMaxSteps() {
        return agentMaxSteps;
    }

    /**
     * 设置 AgentMaxSteps 字段值。
     * 该方法统一字段写入入口，便于后续扩展校验、审计或联动逻辑。
     * 该方法用于配置管理与 Bean 装配，直接影响模块运行效果。
     * @param agentMaxSteps 输入参数 agentMaxSteps，用于参与本次处理流程。
     */
    public void setAgentMaxSteps(int agentMaxSteps) {
        this.agentMaxSteps = agentMaxSteps;
    }
}
