package com.example.demo.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rag")
public class RagPipelineProperties {

    private int chunkSize = 500;
    private int chunkOverlap = 80;
    private int topK = 5;
    private int contextMaxChars = 4000;
    private int recallTopK = 24;
    private int lexicalPoolSize = 300;
    private double vectorWeight = 0.65;
    private double rerankCoverageWeight = 0.20;
    private int agentMaxSteps = 3;

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

    public int getRecallTopK() {
        return recallTopK;
    }

    public void setRecallTopK(int recallTopK) {
        this.recallTopK = recallTopK;
    }

    public int getLexicalPoolSize() {
        return lexicalPoolSize;
    }

    public void setLexicalPoolSize(int lexicalPoolSize) {
        this.lexicalPoolSize = lexicalPoolSize;
    }

    public double getVectorWeight() {
        return vectorWeight;
    }

    public void setVectorWeight(double vectorWeight) {
        this.vectorWeight = vectorWeight;
    }

    public double getRerankCoverageWeight() {
        return rerankCoverageWeight;
    }

    public void setRerankCoverageWeight(double rerankCoverageWeight) {
        this.rerankCoverageWeight = rerankCoverageWeight;
    }

    public int getAgentMaxSteps() {
        return agentMaxSteps;
    }

    public void setAgentMaxSteps(int agentMaxSteps) {
        this.agentMaxSteps = agentMaxSteps;
    }
}
