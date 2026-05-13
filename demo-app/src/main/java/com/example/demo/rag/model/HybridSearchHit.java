package com.example.demo.rag.model;

/**
 * HybridSearchHit记录类型。
 * 该类型负责承载检索阶段的中间结果，便于融合排序与结果转换。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 * @param chunkId 记录字段 chunkId，用于传递该对象的业务数据。
 * @param documentId 记录字段 documentId，用于传递该对象的业务数据。
 * @param documentTitle 记录字段 documentTitle，用于传递该对象的业务数据。
 * @param sourceType 记录字段 sourceType，用于传递该对象的业务数据。
 * @param sourceRef 记录字段 sourceRef，用于传递该对象的业务数据。
 * @param snippet 记录字段 snippet，用于传递该对象的业务数据。
 * @param vectorScore 记录字段 vectorScore，用于传递该对象的业务数据。
 * @param lexicalScore 记录字段 lexicalScore，用于传递该对象的业务数据。
 * @param rerankScore 记录字段 rerankScore，用于传递该对象的业务数据。
 * @param score 记录字段 score，用于传递该对象的业务数据。
 */
public record HybridSearchHit(
        Long chunkId,
        Long documentId,
        String documentTitle,
        String sourceType,
        String sourceRef,
        String snippet,
        double vectorScore,
        double lexicalScore,
        double rerankScore,
        double score
) {
}
