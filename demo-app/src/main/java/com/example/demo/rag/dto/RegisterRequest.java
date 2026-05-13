package com.example.demo.rag.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * RegisterRequest记录类型。
 * 该类型负责封装请求与响应数据，保证接口契约清晰稳定。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 * @param username 记录字段 username，用于传递该对象的业务数据。
 * @param email 记录字段 email，用于传递该对象的业务数据。
 * @param password 记录字段 password，用于传递该对象的业务数据。
 */
public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 64) String username,
        @Email @Size(max = 128) String email,
        @NotBlank @Size(min = 6, max = 128) String password
) {
}
