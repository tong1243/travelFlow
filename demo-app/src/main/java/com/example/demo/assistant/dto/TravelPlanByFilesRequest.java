package com.example.demo.assistant.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record TravelPlanByFilesRequest(
        @NotEmpty(message = "文件编号列表不能为空")
        List<String> fileIds,
        String requirement
) {
}
