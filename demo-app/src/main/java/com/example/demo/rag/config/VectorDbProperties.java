package com.example.demo.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.vector-db")
/**
 * VectorDbProperties类。
 * 该类型负责定义模块配置项和基础 Bean 装配，影响运行时行为。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 */
public class VectorDbProperties {

    private String url = "http://localhost:6333";
    private String apiKey;
    private String collection = "travel_knowledge";
    private int vectorDimension = 1024;
    private String distance = "Cosine";
    private boolean createIfMissing = true;

    /**
     * 获取 Url 字段值。
     * 该方法提供只读访问入口，减少调用方直接操作内部状态的风险。
     * 该方法用于配置管理与 Bean 装配，直接影响模块运行效果。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public String getUrl() {
        return url;
    }

    /**
     * 设置 Url 字段值。
     * 该方法统一字段写入入口，便于后续扩展校验、审计或联动逻辑。
     * 该方法用于配置管理与 Bean 装配，直接影响模块运行效果。
     * @param url 输入参数 url，用于参与本次处理流程。
     */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * 获取 ApiKey 字段值。
     * 该方法提供只读访问入口，减少调用方直接操作内部状态的风险。
     * 该方法用于配置管理与 Bean 装配，直接影响模块运行效果。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public String getApiKey() {
        return apiKey;
    }

    /**
     * 设置 ApiKey 字段值。
     * 该方法统一字段写入入口，便于后续扩展校验、审计或联动逻辑。
     * 该方法用于配置管理与 Bean 装配，直接影响模块运行效果。
     * @param apiKey 输入参数 apiKey，用于参与本次处理流程。
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * 获取 Collection 字段值。
     * 该方法提供只读访问入口，减少调用方直接操作内部状态的风险。
     * 该方法用于配置管理与 Bean 装配，直接影响模块运行效果。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public String getCollection() {
        return collection;
    }

    /**
     * 设置 Collection 字段值。
     * 该方法统一字段写入入口，便于后续扩展校验、审计或联动逻辑。
     * 该方法用于配置管理与 Bean 装配，直接影响模块运行效果。
     * @param collection 输入参数 collection，用于参与本次处理流程。
     */
    public void setCollection(String collection) {
        this.collection = collection;
    }

    /**
     * 获取 VectorDimension 字段值。
     * 该方法提供只读访问入口，减少调用方直接操作内部状态的风险。
     * 该方法用于配置管理与 Bean 装配，直接影响模块运行效果。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public int getVectorDimension() {
        return vectorDimension;
    }

    /**
     * 设置 VectorDimension 字段值。
     * 该方法统一字段写入入口，便于后续扩展校验、审计或联动逻辑。
     * 该方法用于配置管理与 Bean 装配，直接影响模块运行效果。
     * @param vectorDimension 输入参数 vectorDimension，用于参与本次处理流程。
     */
    public void setVectorDimension(int vectorDimension) {
        this.vectorDimension = vectorDimension;
    }

    /**
     * 获取 Distance 字段值。
     * 该方法提供只读访问入口，减少调用方直接操作内部状态的风险。
     * 该方法用于配置管理与 Bean 装配，直接影响模块运行效果。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public String getDistance() {
        return distance;
    }

    /**
     * 设置 Distance 字段值。
     * 该方法统一字段写入入口，便于后续扩展校验、审计或联动逻辑。
     * 该方法用于配置管理与 Bean 装配，直接影响模块运行效果。
     * @param distance 输入参数 distance，用于参与本次处理流程。
     */
    public void setDistance(String distance) {
        this.distance = distance;
    }

    /**
     * 执行 isCreateIfMissing 条件判断。
     * 该方法返回布尔判定结果，用于上层流程分支控制和策略选择。
     * 该方法用于配置管理与 Bean 装配，直接影响模块运行效果。
     * @return 返回判断结果：`true` 表示条件成立，`false` 表示条件不成立。
     */
    public boolean isCreateIfMissing() {
        return createIfMissing;
    }

    /**
     * 设置 CreateIfMissing 字段值。
     * 该方法统一字段写入入口，便于后续扩展校验、审计或联动逻辑。
     * 该方法用于配置管理与 Bean 装配，直接影响模块运行效果。
     * @param createIfMissing 输入参数 createIfMissing，用于参与本次处理流程。
     */
    public void setCreateIfMissing(boolean createIfMissing) {
        this.createIfMissing = createIfMissing;
    }
}
