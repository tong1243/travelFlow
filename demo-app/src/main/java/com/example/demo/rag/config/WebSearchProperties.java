package com.example.demo.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.web-search")
public class WebSearchProperties {

    private boolean enabled = true;
    private String provider = "baidu";
    private String baseUrl = "https://www.baidu.com/s";
    private String fallbackProvider = "duckduckgo";
    private String fallbackBaseUrl = "https://api.duckduckgo.com/";
    private int limit = 5;
    private double minRerankScore = 0.08;
    private int connectTimeoutSeconds = 3;
    private int readTimeoutSeconds = 5;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getFallbackProvider() {
        return fallbackProvider;
    }

    public void setFallbackProvider(String fallbackProvider) {
        this.fallbackProvider = fallbackProvider;
    }

    public String getFallbackBaseUrl() {
        return fallbackBaseUrl;
    }

    public void setFallbackBaseUrl(String fallbackBaseUrl) {
        this.fallbackBaseUrl = fallbackBaseUrl;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public double getMinRerankScore() {
        return minRerankScore;
    }

    public void setMinRerankScore(double minRerankScore) {
        this.minRerankScore = minRerankScore;
    }

    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }

    public int getReadTimeoutSeconds() {
        return readTimeoutSeconds;
    }

    public void setReadTimeoutSeconds(int readTimeoutSeconds) {
        this.readTimeoutSeconds = readTimeoutSeconds;
    }
}
