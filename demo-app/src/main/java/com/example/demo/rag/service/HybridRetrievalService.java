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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class HybridRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(HybridRetrievalService.class);
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{L}\\p{N}]+|[\\p{IsHan}]+");

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final EmbeddingService embeddingService;
    private final QdrantVectorStoreClient vectorStoreClient;
    private final RagPipelineProperties properties;

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

    public List<HybridSearchHit> retrieve(String question, int topK, String sourceType, String sourceRefContains) {
        if (question == null || question.isBlank()) {
            return List.of();
        }

        int finalTopK = Math.max(1, topK);
        int recallTopK = Math.max(finalTopK, Math.max(6, properties.getRecallTopK()));
        double vectorWeight = clamp(properties.getVectorWeight(), 0.05, 0.95);
        double rerankWeight = clamp(properties.getRerankCoverageWeight(), 0.0, 0.45);

        List<KnowledgeDocument> filteredDocuments = filterDocuments(sourceType, sourceRefContains);
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

    private List<KnowledgeDocument> filterDocuments(String sourceType, String sourceRefContains) {
        List<KnowledgeDocument> activeDocuments = documentRepository.findByStatusOrderByUpdatedAtDesc("ACTIVE");
        if (activeDocuments.isEmpty()) {
            return List.of();
        }

        String sourceTypeNorm = normalize(sourceType);
        String sourceRefNorm = normalize(sourceRefContains);
        List<KnowledgeDocument> result = new ArrayList<>();
        for (KnowledgeDocument item : activeDocuments) {
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
            // Keep lexical retrieval available when the vector store is unavailable.
            log.warn("Vector retrieval failed, fallback to lexical only: {}", ex.getMessage());
        }
        return scoreByPoint;
    }

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

    private Map<String, KnowledgeChunk> loadChunksByPointId(Set<String> pointIds) {
        List<String> ids = pointIds.stream().toList();
        List<KnowledgeChunk> chunks = chunkRepository.findByPointIdIn(ids);
        Map<String, KnowledgeChunk> result = new LinkedHashMap<>();
        for (KnowledgeChunk chunk : chunks) {
            result.put(chunk.getPointId(), chunk);
        }
        return result;
    }

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
                // Chinese continuous text is additionally split to bi-grams for better matching.
                terms.add(token);
                terms.addAll(toHanBiGrams(token));
            } else if (token.length() >= 2) {
                terms.add(token);
            }
        }
        return terms;
    }

    private Set<String> tokenizeUniqueTerms(String text) {
        return new LinkedHashSet<>(tokenizeTerms(text));
    }

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

    private String shortSnippet(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String normalized = content.trim().replace('\n', ' ');
        return normalized.length() <= 220 ? normalized : normalized.substring(0, 220) + "...";
    }

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

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private String normalize(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private record ChunkTermStat(
            KnowledgeChunk chunk,
            Map<String, Integer> termFreq,
            int termCount
    ) {
    }

    private record ScoreRange(double min, double max) {

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

        double normalize(double value) {
            if (max <= min) {
                return value <= 0 ? 0.0 : 1.0;
            }
            return (value - min) / (max - min);
        }
    }
}
