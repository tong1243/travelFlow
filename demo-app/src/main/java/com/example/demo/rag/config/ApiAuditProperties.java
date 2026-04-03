package com.example.demo.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app.api-audit")
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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isIncludeAllApi() {
        return includeAllApi;
    }

    public void setIncludeAllApi(boolean includeAllApi) {
        this.includeAllApi = includeAllApi;
    }

    public List<String> getCandidatePrefixes() {
        return candidatePrefixes;
    }

    public void setCandidatePrefixes(List<String> candidatePrefixes) {
        this.candidatePrefixes = candidatePrefixes == null ? new ArrayList<>() : candidatePrefixes;
    }
}

