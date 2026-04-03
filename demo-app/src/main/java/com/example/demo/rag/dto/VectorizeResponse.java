package com.example.demo.rag.dto;

import java.util.List;

public record VectorizeResponse(
        int dimension,
        List<Double> vector
) {
}
