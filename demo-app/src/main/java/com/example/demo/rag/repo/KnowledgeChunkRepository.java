package com.example.demo.rag.repo;

import com.example.demo.rag.entity.KnowledgeChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * KnowledgeChunkRepository接口。
 * 该类型负责声明数据访问能力，由 Spring Data 生成具体实现。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 */
public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, Long> {

    /**
     * 执行 findByDocumentIdOrderByChunkIndexAsc 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法用于定义仓储查询语义，执行逻辑由框架按命名规则生成。
     * @param documentId 输入参数 documentId，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    List<KnowledgeChunk> findByDocumentIdOrderByChunkIndexAsc(Long documentId);

    /**
     * 执行 findByDocumentIdIn 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法用于定义仓储查询语义，执行逻辑由框架按命名规则生成。
     * @param documentIds 输入参数 documentIds，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    List<KnowledgeChunk> findByDocumentIdIn(List<Long> documentIds);

    /**
     * 执行 findByPointIdIn 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法用于定义仓储查询语义，执行逻辑由框架按命名规则生成。
     * @param pointIds 输入参数 pointIds，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    List<KnowledgeChunk> findByPointIdIn(List<String> pointIds);

    /**
     * 执行 countByDocumentId 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法用于定义仓储查询语义，执行逻辑由框架按命名规则生成。
     * @param documentId 输入参数 documentId，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    long countByDocumentId(Long documentId);

    /**
     * 执行 deleteByDocumentId 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法用于定义仓储查询语义，执行逻辑由框架按命名规则生成。
     * @param documentId 输入参数 documentId，用于参与本次处理流程。
     */
    void deleteByDocumentId(Long documentId);
}
