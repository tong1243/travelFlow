package com.example.demo.rag.repo;

import com.example.demo.rag.entity.KnowledgeDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * KnowledgeDocumentRepository接口。
 * 该类型负责声明数据访问能力，由 Spring Data 生成具体实现。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 */
public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

    /**
     * 执行 findByStatusOrderByUpdatedAtDesc 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法用于定义仓储查询语义，执行逻辑由框架按命名规则生成。
     * @param status 输入参数 status，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    List<KnowledgeDocument> findByStatusOrderByUpdatedAtDesc(String status);

    Optional<KnowledgeDocument> findBySourceTypeAndSourceRefAndCreatedBy(String sourceType, String sourceRef, Long createdBy);
}
