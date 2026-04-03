package com.example.demo.rag.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 64) String username,
        @Email @Size(max = 128) String email,
        @NotBlank @Size(min = 6, max = 128) String password
) {
}
