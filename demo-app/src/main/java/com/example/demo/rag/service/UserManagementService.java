package com.example.demo.rag.service;

import com.example.demo.rag.dto.UserProfileResponse;
import com.example.demo.rag.repo.UserAccountRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
/**
 * UserManagementService类。
 * 该类型负责组织核心业务流程，串联检索、存储与模型调用能力。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 */
public class UserManagementService {

    private final UserAccountRepository userAccountRepository;

    /**
     * 构造并初始化 UserManagementService 对象。
     * 该构造方法用于注入运行所需依赖，保证对象创建后即可参与完整流程。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param userAccountRepository 输入参数 userAccountRepository，用于参与本次处理流程。
     */
    public UserManagementService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    /**
     * 执行 listUsers 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public List<UserProfileResponse> listUsers() {
        return userAccountRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                .map(item -> new UserProfileResponse(item.getId(), item.getUsername(), item.getEmail(), item.getRole()))
                .toList();
    }
}
