package com.example.demo.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rag")
public class RagPipelineProperties {

    private int chunkSize = 500;
    private int chunkOverlap = 80;
    private int topK = 5;
    private int contextMaxChars = 4000;

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public int getChunkOverlap() {
        return chunkOverlap;
    }

    public void setChunkOverlap(int chunkOverlap) {
        this.chunkOverlap = chunkOverlap;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public int getContextMaxChars() {
        return contextMaxChars;
    }

    public void setContextMaxChars(int contextMaxChars) {
        this.contextMaxChars = contextMaxChars;
    }
}
