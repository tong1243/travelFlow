package com.example.demo.rag.service;

import com.example.demo.rag.RagException;
import com.example.demo.rag.dto.AuthResponse;
import com.example.demo.rag.dto.LoginRequest;
import com.example.demo.rag.dto.RegisterRequest;
import com.example.demo.rag.dto.UserProfileResponse;
import com.example.demo.rag.entity.UserAccount;
import com.example.demo.rag.repo.UserAccountRepository;
import com.example.demo.rag.security.AuthenticatedUser;
import com.example.demo.rag.security.JwtTokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
/**
 * AuthService类。
 * 该类型负责组织核心业务流程，串联检索、存储与模型调用能力。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 */
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    /**
     * 构造并初始化 AuthService 对象。
     * 该构造方法用于注入运行所需依赖，保证对象创建后即可参与完整流程。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param userAccountRepository 输入参数 userAccountRepository，用于参与本次处理流程。
     * @param passwordEncoder 输入参数 passwordEncoder，用于参与本次处理流程。
     * @param jwtTokenService 输入参数 jwtTokenService，用于参与本次处理流程。
     */
    public AuthService(UserAccountRepository userAccountRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenService jwtTokenService) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    @Transactional
    /**
     * 执行 register 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param request 输入参数 request，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public AuthResponse register(RegisterRequest request) {
        String username = request.username().trim();
        if (userAccountRepository.existsByUsername(username)) {
            throw new RagException("用户名已存在。");
        }

        String email = normalizeEmail(request.email());
        if (email != null && userAccountRepository.existsByEmail(email)) {
            throw new RagException("邮箱已存在。");
        }

        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole("USER");
        user.setEnabled(true);
        user = userAccountRepository.save(user);

        return toAuthResponse(user);
    }

    /**
     * 执行 login 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param request 输入参数 request，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public AuthResponse login(LoginRequest request) {
        UserAccount user = userAccountRepository.findByUsername(request.username().trim())
                .orElseThrow(() -> new RagException("用户名或密码错误。"));
        if (!user.isEnabled()) {
            throw new RagException("账号已被禁用。");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new RagException("用户名或密码错误。");
        }
        return toAuthResponse(user);
    }

    /**
     * 执行 me 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param userId 输入参数 userId，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public UserProfileResponse me(Long userId) {
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new RagException("用户不存在。"));
        return new UserProfileResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole());
    }

    /**
     * 执行 toAuthResponse 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param user 输入参数 user，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    private AuthResponse toAuthResponse(UserAccount user) {
        AuthenticatedUser principal = new AuthenticatedUser(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                user.getRole(),
                user.isEnabled()
        );
        String token = jwtTokenService.createToken(principal);
        return new AuthResponse(user.getId(), user.getUsername(), token, jwtTokenService.getExpireSeconds());
    }

    /**
     * 执行 normalizeEmail 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param email 输入参数 email，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String normalized = email.trim();
        return normalized.isBlank() ? null : normalized;
    }
}
