package com.example.demo.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.flight")
/**
 * FlightLookupProperties配置类。
 * 该类用于管理机票查询外部接口配置：
 * 1) 是否启用；
 * 2) 服务商名称与地址；
 * 3) API Key 与查询条数限制；
 * 4) 连接/读取超时参数。
 * 通过集中配置可在不改代码的情况下切换服务商或关闭外部查询。
 */
public class FlightLookupProperties {

    private boolean enabled = true;
    private String domesticProvider = "ctrip";
    private String internationalProvider = "aviationstack";
    private String provider = "aviationstack";
    private String apiKey = "";
    private String baseUrl = "https://api.aviationstack.com/v1/flights";
    private String ctripBaseUrl = "https://flights.ctrip.com/online/list/oneway-{dep}-{arr}";
    private int limit = 5;
    private int connectTimeoutSeconds = 3;
    private int readTimeoutSeconds = 4;

    /**
     * 获取是否启用机票查询。
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置是否启用机票查询。
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取国内航线服务商标识。
     */
    public String getDomesticProvider() {
        return domesticProvider;
    }

    /**
     * 设置国内航线服务商标识。
     */
    public void setDomesticProvider(String domesticProvider) {
        this.domesticProvider = domesticProvider;
    }

    /**
     * 获取国际航线服务商标识。
     */
    public String getInternationalProvider() {
        return internationalProvider;
    }

    /**
     * 设置国际航线服务商标识。
     */
    public void setInternationalProvider(String internationalProvider) {
        this.internationalProvider = internationalProvider;
    }

    /**
     * 获取机票接口服务商标识。
     * 历史兼容字段，建议优先使用 domestic-provider / international-provider。
     */
    public String getProvider() {
        return provider;
    }

    /**
     * 设置机票接口服务商标识。
     * 历史兼容字段，建议优先使用 domestic-provider / international-provider。
     */
    public void setProvider(String provider) {
        this.provider = provider;
    }

    /**
     * 获取机票接口 API Key。
     */
    public String getApiKey() {
        return apiKey;
    }

    /**
     * 设置机票接口 API Key。
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * 获取机票接口基础地址。
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * 设置机票接口基础地址。
     */
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * 获取携程国内机票查询链接模板。
     */
    public String getCtripBaseUrl() {
        return ctripBaseUrl;
    }

    /**
     * 设置携程国内机票查询链接模板。
     */
    public void setCtripBaseUrl(String ctripBaseUrl) {
        this.ctripBaseUrl = ctripBaseUrl;
    }

    /**
     * 获取机票查询返回条数上限。
     */
    public int getLimit() {
        return limit;
    }

    /**
     * 设置机票查询返回条数上限。
     */
    public void setLimit(int limit) {
        this.limit = limit;
    }

    /**
     * 获取连接超时时长（秒）。
     */
    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    /**
     * 设置连接超时时长（秒）。
     */
    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }

    /**
     * 获取读取超时时长（秒）。
     */
    public int getReadTimeoutSeconds() {
        return readTimeoutSeconds;
    }

    /**
     * 设置读取超时时长（秒）。
     */
    public void setReadTimeoutSeconds(int readTimeoutSeconds) {
        this.readTimeoutSeconds = readTimeoutSeconds;
    }
}
