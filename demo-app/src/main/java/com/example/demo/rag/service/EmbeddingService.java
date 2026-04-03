package com.example.demo.rag.service;

import com.example.demo.assistant.BailianClient;
import com.example.demo.rag.RagException;
import com.example.demo.rag.config.VectorDbProperties;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmbeddingService {

    private final BailianClient bailianClient;
    private final VectorDbProperties vectorDbProperties;

    public EmbeddingService(BailianClient bailianClient, VectorDbProperties vectorDbProperties) {
        this.bailianClient = bailianClient;
        this.vectorDbProperties = vectorDbProperties;
    }

    public List<Double> vectorize(String text) {
        List<Double> vector = bailianClient.embed(text);
        int expected = vectorDbProperties.getVectorDimension();
        if (expected > 0 && vector.size() != expected) {
            throw new RagException("Embedding dimension mismatch. expected=" + expected + ", actual=" + vector.size());
        }
        return vector;
    }
}
