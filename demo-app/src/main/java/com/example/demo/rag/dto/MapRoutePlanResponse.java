package com.example.demo.rag.dto;

import java.util.List;

public record MapRoutePlanResponse(
        String city,
        String profile,
        String summary,
        List<MapRouteSegmentResponse> segments
) {
}

