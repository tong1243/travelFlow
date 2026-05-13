package com.example.demo.assistant.dto;

import jakarta.validation.constraints.NotBlank;

public record TravelFileQaRequest(
        @NotBlank(message = "文件编号不能为空")
        String fileId,
        @NotBlank(message = "问题不能为空")
        String question
) {
}
