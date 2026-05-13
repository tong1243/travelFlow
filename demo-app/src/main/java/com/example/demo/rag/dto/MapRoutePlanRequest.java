package com.example.demo.rag.dto;

import java.util.List;

public record MapRoutePlanRequest(
        String city,
        List<String> places,
        String travelMode
) {
}

