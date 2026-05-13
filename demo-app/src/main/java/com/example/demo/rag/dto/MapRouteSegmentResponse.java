package com.example.demo.rag.dto;

import java.util.List;

public record MapRouteSegmentResponse(
        String from,
        String to,
        double distanceKm,
        int durationMinutes,
        String mapUrl,
        List<MapRoutePointResponse> path
) {
}
