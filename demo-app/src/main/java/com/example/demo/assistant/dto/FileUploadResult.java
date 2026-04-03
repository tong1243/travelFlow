package com.example.demo.assistant.dto;

public record FileUploadResult(
        String fileId,
        String filename,
        Long bytes,
        String purpose
) {
}
