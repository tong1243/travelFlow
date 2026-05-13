package com.example.demo.rag.service;

import com.example.demo.rag.RagException;
import com.example.demo.rag.config.RagPipelineProperties;
import com.example.demo.rag.entity.KnowledgeChunk;
import com.example.demo.rag.entity.KnowledgeDocument;
import com.example.demo.rag.model.HybridSearchHit;
import com.example.demo.rag.model.VectorSearchHit;
import com.example.demo.rag.repo.KnowledgeChunkRepository;
import com.example.demo.rag.repo.KnowledgeDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
/**
 * HybridRetrievalService类。
 * 该类型负责组织核心业务流程，串联检索、存储与模型调用能力。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 */
public class HybridRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(HybridRetrievalService.class);
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{L}\\p{N}]+|[\\p{IsHan}]+");

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final EmbeddingService embeddingService;
    private final QdrantVectorStoreClient vectorStoreClient;
    private final RagPipelineProperties properties;

    /**
     * 构造并初始化 HybridRetrievalService 对象。
     * 该构造方法用于注入运行所需依赖，保证对象创建后即可参与完整流程。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param documentRepository 输入参数 documentRepository，用于参与本次处理流程。
     * @param chunkRepository 输入参数 chunkRepository，用于参与本次处理流程。
     * @param embeddingService 输入参数 embeddingService，用于参与本次处理流程。
     * @param vectorStoreClient 输入参数 vectorStoreClient，用于参与本次处理流程。
     * @param properties 输入参数 properties，用于参与本次处理流程。
     */
    public HybridRetrievalService(KnowledgeDocumentRepository documentRepository,
                                  KnowledgeChunkRepository chunkRepository,
                                  EmbeddingService embeddingService,
                                  QdrantVectorStoreClient vectorStoreClient,
                                  RagPipelineProperties properties) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.embeddingService = embeddingService;
        this.vectorStoreClient = vectorStoreClient;
        this.properties = properties;
    }

    /**
     * 执行 retrieve 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param question 输入参数 question，用于参与本次处理流程。
     * @param topK 输入参数 topK，用于参与本次处理流程。
     * @param sourceType 输入参数 sourceType，用于参与本次处理流程。
     * @param sourceRefContains 输入参数 sourceRefContains，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public List<HybridSearchHit> retrieve(Long operatorId,
                                          boolean isAdmin,
                                          String question,
                                          int topK,
                                          String sourceType,
                                          String sourceRefContains) {
        if (question == null || question.isBlank()) {
            return List.of();
        }

        int finalTopK = Math.max(1, topK);
        int recallTopK = Math.max(finalTopK, Math.max(6, properties.getRecallTopK()));
        double vectorWeight = clamp(properties.getVectorWeight(), 0.05, 0.95);
        double rerankWeight = clamp(properties.getRerankCoverageWeight(), 0.0, 0.45);

        List<KnowledgeDocument> filteredDocuments = filterDocuments(operatorId, isAdmin, sourceType, sourceRefContains);
        if (filteredDocuments.isEmpty()) {
            return List.of();
        }
        Map<Long, KnowledgeDocument> documentById = new LinkedHashMap<>();
        for (KnowledgeDocument document : filteredDocuments) {
            documentById.put(document.getId(), document);
        }
        Set<Long> allowedDocumentIds = documentById.keySet();

        Map<String, Double> vectorScoreByPoint = collectVectorScores(question, recallTopK, allowedDocumentIds);
        Map<String, Double> lexicalScoreByPoint = collectLexicalScores(question, recallTopK, allowedDocumentIds);

        Set<String> mergedPointIds = new LinkedHashSet<>();
        mergedPointIds.addAll(vectorScoreByPoint.keySet());
        mergedPointIds.addAll(lexicalScoreByPoint.keySet());
        if (mergedPointIds.isEmpty()) {
            return List.of();
        }

        Map<String, KnowledgeChunk> chunkByPoint = loadChunksByPointId(mergedPointIds);
        ScoreRange vectorRange = ScoreRange.of(vectorScoreByPoint.values());
        ScoreRange lexicalRange = ScoreRange.of(lexicalScoreByPoint.values());
        Set<String> queryTerms = tokenizeUniqueTerms(question);

        List<HybridSearchHit> mergedHits = new ArrayList<>();
        for (String pointId : mergedPointIds) {
            KnowledgeChunk chunk = chunkByPoint.get(pointId);
            if (chunk == null) {
                continue;
            }
            KnowledgeDocument document = documentById.get(chunk.getDocumentId());
            if (document == null) {
                continue;
            }

            double vectorRaw = vectorScoreByPoint.getOrDefault(pointId, 0.0);
            double lexicalRaw = lexicalScoreByPoint.getOrDefault(pointId, 0.0);
            double vectorNormalized = vectorRange.normalize(vectorRaw);
            double lexicalNormalized = lexicalRange.normalize(lexicalRaw);
            double fusedScore = vectorWeight * vectorNormalized + (1.0 - vectorWeight) * lexicalNormalized;
            double rerankCoverage = coverageScore(queryTerms, chunk.getContent());
            double finalScore = fusedScore * (1.0 - rerankWeight) + rerankCoverage * rerankWeight;

            mergedHits.add(new HybridSearchHit(
                    chunk.getId(),
                    chunk.getDocumentId(),
                    document.getTitle(),
                    document.getSourceType(),
                    document.getSourceRef(),
                    shortSnippet(chunk.getContent()),
                    vectorRaw,
                    lexicalRaw,
                    rerankCoverage,
                    finalScore
            ));
        }

        mergedHits.sort(Comparator.comparingDouble(HybridSearchHit::score).reversed());
        return mergedHits.size() <= finalTopK ? mergedHits : mergedHits.subList(0, finalTopK);
    }

    /**
     * 执行 filterDocuments 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param sourceType 输入参数 sourceType，用于参与本次处理流程。
     * @param sourceRefContains 输入参数 sourceRefContains，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    private List<KnowledgeDocument> filterDocuments(Long operatorId, boolean isAdmin, String sourceType, String sourceRefContains) {
        List<KnowledgeDocument> activeDocuments = documentRepository.findByStatusOrderByUpdatedAtDesc("ACTIVE");
        if (activeDocuments.isEmpty()) {
            return List.of();
        }

        String sourceTypeNorm = normalize(sourceType);
        String sourceRefNorm = normalize(sourceRefContains);
        List<KnowledgeDocument> result = new ArrayList<>();
        for (KnowledgeDocument item : activeDocuments) {
            if (!isVisibleToUser(item, operatorId, isAdmin)) {
                continue;
            }
            if (sourceTypeNorm != null) {
                String itemSourceType = normalize(item.getSourceType());
                if (itemSourceType == null || !itemSourceType.equals(sourceTypeNorm)) {
                    continue;
                }
            }
            if (sourceRefNorm != null) {
                String itemSourceRef = normalize(item.getSourceRef());
                if (itemSourceRef == null || !itemSourceRef.contains(sourceRefNorm)) {
                    continue;
                }
            }
            result.add(item);
        }
        return result;
    }

    private boolean isVisibleToUser(KnowledgeDocument item, Long operatorId, boolean isAdmin) {
        if (isAdmin) {
            return true;
        }
        Long createdBy = item.getCreatedBy();
        if (createdBy == null) {
            return true;
        }
        return operatorId != null && Objects.equals(createdBy, operatorId);
    }

    /**
     * 执行 collectVectorScores 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param question 输入参数 question，用于参与本次处理流程。
     * @param recallTopK 输入参数 recallTopK，用于参与本次处理流程。
     * @param allowedDocumentIds 输入参数 allowedDocumentIds，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    private Map<String, Double> collectVectorScores(String question, int recallTopK, Set<Long> allowedDocumentIds) {
        Map<String, Double> scoreByPoint = new LinkedHashMap<>();
        try {
            List<Double> queryVector = embeddingService.vectorize(question);
            List<VectorSearchHit> vectorHits = vectorStoreClient.search(queryVector, recallTopK);
            for (VectorSearchHit hit : vectorHits) {
                String pointId = hit.pointId();
                if (pointId == null || pointId.isBlank()) {
                    continue;
                }
                Long documentId = asLong(hit.payload() == null ? null : hit.payload().get("documentId"));
                if (documentId != null && !allowedDocumentIds.contains(documentId)) {
                    continue;
                }
                double score = Math.max(0.0, hit.score());
                scoreByPoint.merge(pointId, score, Math::max);
            }
        } catch (RagException ex) {
            // 向量检索失败时保留词法检索能力，避免整条链路不可用。
            log.warn("Vector retrieval failed, fallback to lexical only: {}", ex.getMessage());
        }
        return scoreByPoint;
    }

    /**
     * 执行 collectLexicalScores 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param question 输入参数 question，用于参与本次处理流程。
     * @param recallTopK 输入参数 recallTopK，用于参与本次处理流程。
     * @param allowedDocumentIds 输入参数 allowedDocumentIds，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    private Map<String, Double> collectLexicalScores(String question, int recallTopK, Set<Long> allowedDocumentIds) {
        Set<String> queryTerms = tokenizeUniqueTerms(question);
        if (queryTerms.isEmpty()) {
            return Map.of();
        }

        List<Long> documentIds = new ArrayList<>(allowedDocumentIds);
        if (documentIds.isEmpty()) {
            return Map.of();
        }

        List<KnowledgeChunk> chunks = chunkRepository.findByDocumentIdIn(documentIds);
        if (chunks.isEmpty()) {
            return Map.of();
        }
        chunks.sort(Comparator.comparing(KnowledgeChunk::getId, Comparator.nullsLast(Long::compareTo)).reversed());
        int lexicalPoolSize = Math.max(20, properties.getLexicalPoolSize());
        if (chunks.size() > lexicalPoolSize) {
            chunks = chunks.subList(0, lexicalPoolSize);
        }

        List<ChunkTermStat> stats = new ArrayList<>(chunks.size());
        Map<String, Integer> docFreq = new HashMap<>();
        double totalLen = 0;

        for (KnowledgeChunk chunk : chunks) {
            List<String> terms = tokenizeTerms(chunk.getContent());
            if (terms.isEmpty()) {
                continue;
            }
            Map<String, Integer> tf = new HashMap<>();
            for (String term : terms) {
                tf.merge(term, 1, Integer::sum);
            }
            Set<String> uniqueTerms = new HashSet<>(terms);
            for (String term : uniqueTerms) {
                if (queryTerms.contains(term)) {
                    docFreq.merge(term, 1, Integer::sum);
                }
            }
            stats.add(new ChunkTermStat(chunk, tf, terms.size()));
            totalLen += terms.size();
        }
        if (stats.isEmpty()) {
            return Map.of();
        }

        double avgLen = Math.max(1.0, totalLen / stats.size());
        Map<String, Double> lexicalScoreByPoint = new LinkedHashMap<>();
        for (ChunkTermStat stat : stats) {
            double score = bm25Score(stat, queryTerms, docFreq, stats.size(), avgLen);
            if (score <= 0.0 || stat.chunk().getPointId() == null || stat.chunk().getPointId().isBlank()) {
                continue;
            }
            lexicalScoreByPoint.put(stat.chunk().getPointId(), score);
        }

        List<Map.Entry<String, Double>> sorted = new ArrayList<>(lexicalScoreByPoint.entrySet());
        sorted.sort(Map.Entry.<String, Double>comparingByValue().reversed());
        int limit = Math.min(sorted.size(), recallTopK);
        Map<String, Double> limited = new LinkedHashMap<>();
        for (int i = 0; i < limit; i++) {
            Map.Entry<String, Double> item = sorted.get(i);
            limited.put(item.getKey(), item.getValue());
        }
        return limited;
    }

    /**
     * 执行 bm25Score 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param stat 输入参数 stat，用于参与本次处理流程。
     * @param queryTerms 输入参数 queryTerms，用于参与本次处理流程。
     * @param docFreq 输入参数 docFreq，用于参与本次处理流程。
     * @param docCount 输入参数 docCount，用于参与本次处理流程。
     * @param avgLen 输入参数 avgLen，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    private double bm25Score(ChunkTermStat stat,
                             Set<String> queryTerms,
                             Map<String, Integer> docFreq,
                             int docCount,
                             double avgLen) {
        double k1 = 1.2;
        double b = 0.75;
        double norm = k1 * (1.0 - b + b * (stat.termCount() / avgLen));
        double score = 0.0;
        for (String term : queryTerms) {
            int tf = stat.termFreq().getOrDefault(term, 0);
            if (tf <= 0) {
                continue;
            }
            int df = Math.max(1, docFreq.getOrDefault(term, 1));
            double idf = Math.log(1 + (docCount - df + 0.5) / (df + 0.5));
            score += idf * ((tf * (k1 + 1.0)) / (tf + norm));
        }
        return score;
    }

    /**
     * 执行 coverageScore 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param queryTerms 输入参数 queryTerms，用于参与本次处理流程。
     * @param content 输入参数 content，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    private double coverageScore(Set<String> queryTerms, String content) {
        if (queryTerms.isEmpty() || content == null || content.isBlank()) {
            return 0.0;
        }
        Set<String> contentTerms = tokenizeUniqueTerms(content);
        if (contentTerms.isEmpty()) {
            return 0.0;
        }
        int covered = 0;
        for (String term : queryTerms) {
            if (contentTerms.contains(term)) {
                covered++;
            }
        }
        return (double) covered / queryTerms.size();
    }

    /**
     * 执行 loadChunksByPointId 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param pointIds 输入参数 pointIds，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    private Map<String, KnowledgeChunk> loadChunksByPointId(Set<String> pointIds) {
        List<String> ids = pointIds.stream().toList();
        List<KnowledgeChunk> chunks = chunkRepository.findByPointIdIn(ids);
        Map<String, KnowledgeChunk> result = new LinkedHashMap<>();
        for (KnowledgeChunk chunk : chunks) {
            result.put(chunk.getPointId(), chunk);
        }
        return result;
    }

    /**
     * 执行 tokenizeTerms 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param text 输入参数 text，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    private List<String> tokenizeTerms(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        Matcher matcher = TOKEN_PATTERN.matcher(normalized);
        List<String> terms = new ArrayList<>();
        while (matcher.find()) {
            String token = matcher.group();
            if (token == null || token.isBlank()) {
                continue;
            }
            if (isHanToken(token)) {
                // 中文连续文本额外拆分为双字词，提高匹配效果。
                terms.add(token);
                terms.addAll(toHanBiGrams(token));
            } else if (token.length() >= 2) {
                terms.add(token);
            }
        }
        return terms;
    }

    /**
     * 执行 tokenizeUniqueTerms 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param text 输入参数 text，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    private Set<String> tokenizeUniqueTerms(String text) {
        return new LinkedHashSet<>(tokenizeTerms(text));
    }

    /**
     * 执行 toHanBiGrams 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param token 输入参数 token，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    private List<String> toHanBiGrams(String token) {
        if (token.length() < 2) {
            return List.of(token);
        }
        List<String> result = new ArrayList<>();
        for (int i = 0; i < token.length() - 1; i++) {
            result.add(token.substring(i, i + 2));
        }
        return result;
    }

    /**
     * 执行 isHanToken 条件判断。
     * 该方法返回布尔判定结果，用于上层流程分支控制和策略选择。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param token 输入参数 token，用于参与本次处理流程。
     * @return 返回判断结果：`true` 表示条件成立，`false` 表示条件不成立。
     */
    private boolean isHanToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        for (int i = 0; i < token.length(); i++) {
            Character.UnicodeScript script = Character.UnicodeScript.of(token.charAt(i));
            if (script != Character.UnicodeScript.HAN) {
                return false;
            }
        }
        return true;
    }

    /**
     * 执行 shortSnippet 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param content 输入参数 content，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    private String shortSnippet(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String normalized = content.trim().replace('\n', ' ');
        return normalized.length() <= 220 ? normalized : normalized.substring(0, 220) + "...";
    }

    /**
     * 执行 asLong 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param value 输入参数 value，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 执行 clamp 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param value 输入参数 value，用于参与本次处理流程。
     * @param min 输入参数 min，用于参与本次处理流程。
     * @param max 输入参数 max，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 执行 normalize 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param text 输入参数 text，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    private String normalize(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    /**
     * ChunkTermStat记录类型。
     * 该类型负责组织核心业务流程，串联检索、存储与模型调用能力。
     * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
     * @param chunk 记录字段 chunk，用于传递该对象的业务数据。
     * @param termFreq 记录字段 termFreq，用于传递该对象的业务数据。
     * @param termCount 记录字段 termCount，用于传递该对象的业务数据。
     */
    private record ChunkTermStat(
            KnowledgeChunk chunk,
            Map<String, Integer> termFreq,
            int termCount
    ) {
    }

    /**
     * ScoreRange记录类型。
     * 该类型负责组织核心业务流程，串联检索、存储与模型调用能力。
     * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
     * @param min 记录字段 min，用于传递该对象的业务数据。
     * @param max 记录字段 max，用于传递该对象的业务数据。
     */
    private record ScoreRange(double min, double max) {

        /**
         * 执行 of 业务处理。
         * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
         * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
         * @param values 输入参数 values，用于参与本次处理流程。
         * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
         */
        static ScoreRange of(Collection<Double> values) {
            if (values == null || values.isEmpty()) {
                return new ScoreRange(0.0, 0.0);
            }
            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;
            for (Double item : values) {
                if (item == null) {
                    continue;
                }
                min = Math.min(min, item);
                max = Math.max(max, item);
            }
            if (Double.isInfinite(min) || Double.isInfinite(max)) {
                return new ScoreRange(0.0, 0.0);
            }
            return new ScoreRange(min, max);
        }

        /**
         * 执行 normalize 业务处理。
         * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
         * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
         * @param value 输入参数 value，用于参与本次处理流程。
         * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
         */
        double normalize(double value) {
            if (max <= min) {
                return value <= 0 ? 0.0 : 1.0;
            }
            return (value - min) / (max - min);
        }
    }
}
