package com.example.demo.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.vector-db")
public class VectorDbProperties {

    private String url = "http://localhost:6333";
    private String apiKey;
    private String collection = "travel_knowledge";
    private int vectorDimension = 1024;
    private String distance = "Cosine";
    private boolean createIfMissing = true;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getCollection() {
        return collection;
    }

    public void setCollection(String collection) {
        this.collection = collection;
    }

    public int getVectorDimension() {
        return vectorDimension;
    }

    public void setVectorDimension(int vectorDimension) {
        this.vectorDimension = vectorDimension;
    }

    public String getDistance() {
        return distance;
    }

    public void setDistance(String distance) {
        this.distance = distance;
    }

    public boolean isCreateIfMissing() {
        return createIfMissing;
    }

    public void setCreateIfMissing(boolean createIfMissing) {
        this.createIfMissing = createIfMissing;
    }
}
