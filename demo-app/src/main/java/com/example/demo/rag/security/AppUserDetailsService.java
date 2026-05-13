package com.example.demo.rag.security;

import com.example.demo.rag.RagException;
import com.example.demo.rag.entity.UserAccount;
import com.example.demo.rag.repo.UserAccountRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
/**
 * AppUserDetailsService类。
 * 该类型负责认证授权与访问控制，保障系统安全边界。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 */
public class AppUserDetailsService implements UserDetailsService {

    private final UserAccountRepository userAccountRepository;

    /**
     * 构造并初始化 AppUserDetailsService 对象。
     * 该构造方法用于注入运行所需依赖，保证对象创建后即可参与完整流程。
     * 该方法用于认证授权处理，确保访问链路符合安全策略。
     * @param userAccountRepository 输入参数 userAccountRepository，用于参与本次处理流程。
     */
    public AppUserDetailsService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    /**
     * 执行 loadUserByUsername 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法用于认证授权处理，确保访问链路符合安全策略。
     * @param username 输入参数 username，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserAccount user = userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在：" + username));
        return toPrincipal(user);
    }

    /**
     * 执行 loadByUserId 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法用于认证授权处理，确保访问链路符合安全策略。
     * @param userId 输入参数 userId，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public AuthenticatedUser loadByUserId(Long userId) {
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new RagException("用户不存在：" + userId));
        return toPrincipal(user);
    }

    /**
     * 执行 toPrincipal 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法用于认证授权处理，确保访问链路符合安全策略。
     * @param user 输入参数 user，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    private AuthenticatedUser toPrincipal(UserAccount user) {
        return new AuthenticatedUser(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                user.getRole(),
                user.isEnabled()
        );
    }
}
