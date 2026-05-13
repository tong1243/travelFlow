package com.example.demo.rag.dto;

/**
 * AuthResponse记录类型。
 * 该类型负责封装请求与响应数据，保证接口契约清晰稳定。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 * @param userId 记录字段 userId，用于传递该对象的业务数据。
 * @param username 记录字段 username，用于传递该对象的业务数据。
 * @param token 记录字段 token，用于传递该对象的业务数据。
 * @param expiresInSeconds 记录字段 expiresInSeconds，用于传递该对象的业务数据。
 */
public record AuthResponse(
        Long userId,
        String username,
        String token,
        long expiresInSeconds
) {
}
