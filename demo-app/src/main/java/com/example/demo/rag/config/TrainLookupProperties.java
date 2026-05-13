package com.example.demo.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.train")
public class TrainLookupProperties {

    private boolean enabled = true;
    private String baseUrl = "https://kyfw.12306.cn/otn/leftTicket/query";
    private List<String> alternativeBaseUrls = List.of(
            "https://kyfw.12306.cn/otn/leftTicket/queryG",
            "https://kyfw.12306.cn/otn/leftTicket/queryO"
    );
    private boolean cookieBootstrapEnabled = true;
    private String bootstrapUrl = "https://kyfw.12306.cn/otn/leftTicket/init";
    private int limit = 6;
    private int connectTimeoutSeconds = 3;
    private int readTimeoutSeconds = 4;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public List<String> getAlternativeBaseUrls() {
        return alternativeBaseUrls;
    }

    public void setAlternativeBaseUrls(List<String> alternativeBaseUrls) {
        this.alternativeBaseUrls = alternativeBaseUrls;
    }

    public boolean isCookieBootstrapEnabled() {
        return cookieBootstrapEnabled;
    }

    public void setCookieBootstrapEnabled(boolean cookieBootstrapEnabled) {
        this.cookieBootstrapEnabled = cookieBootstrapEnabled;
    }

    public String getBootstrapUrl() {
        return bootstrapUrl;
    }

    public void setBootstrapUrl(String bootstrapUrl) {
        this.bootstrapUrl = bootstrapUrl;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
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
