package com.example.demo.rag.controller;

import com.example.demo.rag.dto.VectorSearchRequest;
import com.example.demo.rag.dto.VectorSearchResponse;
import com.example.demo.rag.dto.VectorUpsertRequest;
import com.example.demo.rag.dto.VectorizeRequest;
import com.example.demo.rag.dto.VectorizeResponse;
import com.example.demo.rag.service.VectorService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/vector")
@Deprecated(since = "2026-04", forRemoval = false)
public class VectorController {

    private final VectorService vectorService;

    public VectorController(VectorService vectorService) {
        this.vectorService = vectorService;
    }

    @PostMapping("/embed")
    public VectorizeResponse embed(@Valid @RequestBody VectorizeRequest request) {
        return vectorService.vectorize(request.text());
    }

    @PostMapping("/search")
    public VectorSearchResponse search(@Valid @RequestBody VectorSearchRequest request) {
        return vectorService.search(request);
    }

    @PostMapping("/upsert")
    public Map<String, String> upsert(@Valid @RequestBody VectorUpsertRequest request) {
        String pointId = vectorService.upsert(request);
        return Map.of("pointId", pointId);
    }
}
