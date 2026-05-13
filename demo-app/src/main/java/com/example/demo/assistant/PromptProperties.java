package com.example.demo.assistant;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.prompt")
public class PromptProperties {

    /**
     * 外部提示词目录（可选）。
     * 配置后将优先从该目录读取 main_prompt.txt、rag_summarize.txt、report_prompt.txt。
     */
    private String externalDir;

    public String getExternalDir() {
        return externalDir;
    }

    public void setExternalDir(String externalDir) {
        this.externalDir = externalDir;
    }
}
