package com.example.demo.rag.dto;

/**
 * UserProfileResponse记录类型。
 * 该类型负责封装请求与响应数据，保证接口契约清晰稳定。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 * @param userId 记录字段 userId，用于传递该对象的业务数据。
 * @param username 记录字段 username，用于传递该对象的业务数据。
 * @param email 记录字段 email，用于传递该对象的业务数据。
 * @param role 记录字段 role，用于传递该对象的业务数据。
 */
public record UserProfileResponse(
        Long userId,
        String username,
        String email,
        String role
) {
}
