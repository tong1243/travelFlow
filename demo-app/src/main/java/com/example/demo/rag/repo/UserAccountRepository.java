package com.example.demo.rag.repo;

import com.example.demo.rag.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * UserAccountRepository接口。
 * 该类型负责声明数据访问能力，由 Spring Data 生成具体实现。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 */
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    /**
     * 执行 findByUsername 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法用于定义仓储查询语义，执行逻辑由框架按命名规则生成。
     * @param username 输入参数 username，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    Optional<UserAccount> findByUsername(String username);

    /**
     * 执行 existsByUsername 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法用于定义仓储查询语义，执行逻辑由框架按命名规则生成。
     * @param username 输入参数 username，用于参与本次处理流程。
     * @return 返回判断结果：`true` 表示条件成立，`false` 表示条件不成立。
     */
    boolean existsByUsername(String username);

    /**
     * 执行 existsByEmail 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法用于定义仓储查询语义，执行逻辑由框架按命名规则生成。
     * @param email 输入参数 email，用于参与本次处理流程。
     * @return 返回判断结果：`true` 表示条件成立，`false` 表示条件不成立。
     */
    boolean existsByEmail(String email);
}
