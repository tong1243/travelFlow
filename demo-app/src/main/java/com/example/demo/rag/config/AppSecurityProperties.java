package com.example.demo.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public class AppSecurityProperties {

    private String jwtSecret;
    private long jwtExpireSeconds = 86_400;

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public long getJwtExpireSeconds() {
        return jwtExpireSeconds;
    }

    public void setJwtExpireSeconds(long jwtExpireSeconds) {
        this.jwtExpireSeconds = jwtExpireSeconds;
    }
}
