package com.example.demo.assistant.dto;

public record AdminSpotCardResponse(
        Long id,
        String title,
        String location,
        String price,
        String rating,
        String image,
        int sortOrder,
        boolean enabled
) {
}
