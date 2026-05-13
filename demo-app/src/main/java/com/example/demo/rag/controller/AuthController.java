package com.example.demo.rag.controller;

import com.example.demo.rag.dto.AuthResponse;
import com.example.demo.rag.dto.LoginRequest;
import com.example.demo.rag.dto.RegisterRequest;
import com.example.demo.rag.dto.UserProfileResponse;
import com.example.demo.rag.security.AuthenticatedUser;
import com.example.demo.rag.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
/**
 * AuthController类。
 * 该类型负责接收并处理接口请求，协调服务层完成业务响应。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 */
public class AuthController {

    private final AuthService authService;

    /**
     * 构造并初始化 AuthController 对象。
     * 该构造方法用于注入运行所需依赖，保证对象创建后即可参与完整流程。
     * 该方法位于控制层，负责参数承接、上下文透传和响应封装。
     * @param authService 输入参数 authService，用于参与本次处理流程。
     */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    /**
     * 执行 register 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于控制层，负责参数承接、上下文透传和响应封装。
     * @param request 输入参数 request，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    /**
     * 执行 login 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于控制层，负责参数承接、上下文透传和响应封装。
     * @param request 输入参数 request，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
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
}
