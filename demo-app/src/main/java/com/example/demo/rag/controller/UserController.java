package com.example.demo.rag.controller;

import com.example.demo.rag.dto.UserProfileResponse;
import com.example.demo.rag.security.AuthenticatedUser;
import com.example.demo.rag.service.AuthService;
import com.example.demo.rag.service.UserManagementService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
/**
 * UserController类。
 * 该类型负责接收并处理接口请求，协调服务层完成业务响应。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 */
public class UserController {

    private final AuthService authService;
    private final UserManagementService userManagementService;

    /**
     * 构造并初始化 UserController 对象。
     * 该构造方法用于注入运行所需依赖，保证对象创建后即可参与完整流程。
     * 该方法位于控制层，负责参数承接、上下文透传和响应封装。
     * @param authService 输入参数 authService，用于参与本次处理流程。
     * @param userManagementService 输入参数 userManagementService，用于参与本次处理流程。
     */
    public UserController(AuthService authService, UserManagementService userManagementService) {
        this.authService = authService;
        this.userManagementService = userManagementService;
    }

    @GetMapping("/me")
    @Deprecated(since = "2026-04", forRemoval = false)
    /**
     * 执行 me 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于控制层，负责参数承接、上下文透传和响应封装。
     * @param user 输入参数 user，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public UserProfileResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return authService.me(user.getId());
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Deprecated(since = "2026-04", forRemoval = false)
    /**
     * 执行 listUsers 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于控制层，负责参数承接、上下文透传和响应封装。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public List<UserProfileResponse> listUsers() {
        return userManagementService.listUsers();
    }
}
