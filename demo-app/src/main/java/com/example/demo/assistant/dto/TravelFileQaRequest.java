package com.example.demo.assistant.dto;

import jakarta.validation.constraints.NotBlank;

public record TravelFileQaRequest(
        @NotBlank(message = "fileId 不能为空")
        String fileId,
        @NotBlank(message = "question 不能为空")
        String question
) {
}
