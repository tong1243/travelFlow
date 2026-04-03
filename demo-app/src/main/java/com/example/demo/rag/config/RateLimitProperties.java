package com.example.demo.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    private int perMinute = 120;

    public int getPerMinute() {
        return perMinute;
    }

    public void setPerMinute(int perMinute) {
        this.perMinute = perMinute;
    }
}
