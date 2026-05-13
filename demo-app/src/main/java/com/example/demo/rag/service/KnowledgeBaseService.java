package com.example.demo.rag.service;

import com.example.demo.rag.RagException;
import com.example.demo.rag.dto.KnowledgeDocumentDetailResponse;
import com.example.demo.rag.dto.KnowledgeDocumentResponse;
import com.example.demo.rag.dto.KnowledgeSeedResponse;
import com.example.demo.rag.dto.KnowledgeUpsertRequest;
import com.example.demo.rag.dto.RagReferenceItem;
import com.example.demo.rag.entity.KnowledgeChunk;
import com.example.demo.rag.entity.KnowledgeDocument;
import com.example.demo.rag.model.HybridSearchHit;
import com.example.demo.rag.model.VectorSearchHit;
import com.example.demo.rag.repo.KnowledgeChunkRepository;
import com.example.demo.rag.repo.KnowledgeDocumentRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class KnowledgeBaseService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String SOURCE_TYPE_POPULAR_ATTRACTION = "POPULAR_ATTRACTION";
    private static final String SOURCE_REF_PREFIX = "popular-attraction:";
    private static final String POPULAR_ATTRACTIONS_RESOURCE = "classpath:knowledge/popular_attractions_zh.json";

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final TextChunkingService textChunkingService;
    private final EmbeddingService embeddingService;
    private final QdrantVectorStoreClient vectorStoreClient;
    private final KnowledgeFileParserService fileParserService;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    public KnowledgeBaseService(KnowledgeDocumentRepository documentRepository,
                                KnowledgeChunkRepository chunkRepository,
                                TextChunkingService textChunkingService,
                                EmbeddingService embeddingService,
                                QdrantVectorStoreClient vectorStoreClient,
                                KnowledgeFileParserService fileParserService,
                                ObjectMapper objectMapper,
                                ResourceLoader resourceLoader) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.textChunkingService = textChunkingService;
        this.embeddingService = embeddingService;
        this.vectorStoreClient = vectorStoreClient;
        this.fileParserService = fileParserService;
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
    }

    @Transactional
    public KnowledgeDocumentResponse createDocument(KnowledgeUpsertRequest request, Long operatorId) {
        return upsertInternal(null, request, operatorId, false, false);
    }

    @Transactional
    public KnowledgeDocumentResponse updateDocument(Long documentId, KnowledgeUpsertRequest request, Long operatorId) {
        return updateDocument(documentId, request, operatorId, false);
    }

    @Transactional
    public KnowledgeDocumentResponse updateDocument(Long documentId,
                                                    KnowledgeUpsertRequest request,
                                                    Long operatorId,
                                                    boolean isAdmin) {
        return upsertInternal(documentId, request, operatorId, true, isAdmin);
    }

    @Transactional
    public void deleteDocument(Long documentId) {
        deleteDocument(documentId, null, true);
    }

    @Transactional
    public void deleteDocument(Long documentId, Long operatorId, boolean isAdmin) {
        KnowledgeDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RagException("閻儴鐦戦弬鍥ㄣ€傛稉宥呯摠閸? " + documentId));
        assertCanManageDocument(document, operatorId, isAdmin);

        List<KnowledgeChunk> chunks = chunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);
        List<String> pointIds = chunks.stream().map(KnowledgeChunk::getPointId).toList();
        vectorStoreClient.deletePoints(pointIds);
        chunkRepository.deleteByDocumentId(documentId);
        documentRepository.delete(document);
    }

    public List<KnowledgeDocumentResponse> listDocuments() {
        return documentRepository.findAll(Sort.by(Sort.Direction.DESC, "updatedAt")).stream()
                .map(this::toDocumentResponse)
                .toList();
    }

    public List<KnowledgeDocumentResponse> listDocuments(Long operatorId, boolean isAdmin) {
        return documentRepository.findAll(Sort.by(Sort.Direction.DESC, "updatedAt")).stream()
                .filter(document -> isVisibleToUser(document, operatorId, isAdmin))
                .map(this::toDocumentResponse)
                .toList();
    }

    /**
     * 查询单条知识文档详情。
     * 用于前端“编辑偏好”场景，返回正文内容用于回填编辑器。
     *
     * @param documentId 文档ID
     * @param operatorId 当前用户ID
     * @param isAdmin 是否管理员
     * @return 文档详情响应
     */
    public KnowledgeDocumentDetailResponse getDocumentDetail(Long documentId, Long operatorId, boolean isAdmin) {
        KnowledgeDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RagException("知识文档不存在，文档ID=" + documentId));
        if (!isVisibleToUser(document, operatorId, isAdmin)) {
            throw new RagException("当前用户无权限访问该知识文档。");
        }
        return toDocumentDetailResponse(document);
    }

    public List<KnowledgeDocument> listVisibleActiveDocuments(Long operatorId, boolean isAdmin) {
        return documentRepository.findByStatusOrderByUpdatedAtDesc(STATUS_ACTIVE).stream()
                .filter(document -> isVisibleToUser(document, operatorId, isAdmin))
                .toList();
    }

    @Transactional
    public KnowledgeSeedResponse seedPopularAttractions(Long operatorId, boolean overwrite) {
        Long targetOwner = operatorId;
        boolean systemImport = targetOwner == null;
        List<PopularAttractionSeedItem> seeds = loadPopularAttractionSeeds();
        int created = 0;
        int updated = 0;
        int skipped = 0;

        for (PopularAttractionSeedItem item : seeds) {
            String sourceRef = SOURCE_REF_PREFIX + item.slug();
            KnowledgeUpsertRequest request = new KnowledgeUpsertRequest(
                    item.title(),
                    item.content(),
                    SOURCE_TYPE_POPULAR_ATTRACTION,
                    sourceRef
            );
            var existing = documentRepository.findBySourceTypeAndSourceRefAndCreatedBy(
                    SOURCE_TYPE_POPULAR_ATTRACTION,
                    sourceRef,
                    targetOwner
            );
            if (existing.isPresent()) {
                if (!overwrite) {
                    skipped++;
                    continue;
                }
                upsertInternal(existing.get().getId(), request, targetOwner, true, systemImport);
                updated++;
                continue;
            }
            upsertInternal(null, request, targetOwner, false, systemImport);
            created++;
        }
        return new KnowledgeSeedResponse(seeds.size(), created, updated, skipped);
    }

    public KnowledgeUpsertRequest parseUploadToRequest(String title,
                                                       String sourceType,
                                                       String sourceRef,
                                                       String originalFilename,
                                                       byte[] bytes) {
        String content = fileParserService.parseContent(originalFilename, bytes);
        String safeTitle = normalizeTitle(title, originalFilename);
        String safeSourceRef = normalizeSourceRef(sourceRef, originalFilename);
        return new KnowledgeUpsertRequest(safeTitle, content, sourceType, safeSourceRef);
    }

    public KnowledgeUpsertRequest parseUploadToRequest(String title, String sourceType, String sourceRef, byte[] bytes) {
        return parseUploadToRequest(title, sourceType, sourceRef, null, bytes);
    }

    public List<RagReferenceItem> toReferenceItems(List<VectorSearchHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }

        Map<String, Double> scoreByPointId = new LinkedHashMap<>();
        for (VectorSearchHit hit : hits) {
            scoreByPointId.put(hit.pointId(), hit.score());
        }

        List<String> pointIds = new ArrayList<>(scoreByPointId.keySet());
        List<KnowledgeChunk> chunks = chunkRepository.findByPointIdIn(pointIds);
        Map<String, KnowledgeChunk> chunkByPointId = chunks.stream()
                .collect(Collectors.toMap(KnowledgeChunk::getPointId, item -> item, (a, b) -> a));
        Map<Long, KnowledgeDocument> docsById = documentRepository.findAllById(
                chunks.stream().map(KnowledgeChunk::getDocumentId).distinct().toList()
        ).stream().collect(Collectors.toMap(KnowledgeDocument::getId, item -> item, (a, b) -> a));

        List<RagReferenceItem> result = new ArrayList<>();
        for (String pointId : pointIds) {
            KnowledgeChunk chunk = chunkByPointId.get(pointId);
            if (chunk == null) {
                continue;
            }
            KnowledgeDocument document = docsById.get(chunk.getDocumentId());
            String title = document == null ? "未知文档" : document.getTitle();
            String sourceType = document == null ? null : document.getSourceType();
            String sourceRef = document == null ? null : document.getSourceRef();
            double vectorScore = scoreByPointId.getOrDefault(pointId, 0.0);
            result.add(new RagReferenceItem(
                    chunk.getId(),
                    chunk.getDocumentId(),
                    title,
                    sourceType,
                    sourceRef,
                    vectorScore,
                    0.0,
                    0.0,
                    vectorScore,
                    shortSnippet(chunk.getContent())
            ));
        }
        return result;
    }

    public List<RagReferenceItem> toHybridReferenceItems(List<HybridSearchHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        List<RagReferenceItem> result = new ArrayList<>(hits.size());
        for (HybridSearchHit hit : hits) {
            result.add(new RagReferenceItem(
                    hit.chunkId(),
                    hit.documentId(),
                    hit.documentTitle(),
                    hit.sourceType(),
                    hit.sourceRef(),
                    hit.vectorScore(),
                    hit.lexicalScore(),
                    hit.rerankScore(),
                    hit.score(),
                    hit.snippet()
            ));
        }
        return result;
    }

    private KnowledgeDocumentResponse upsertInternal(Long documentId,
                                                     KnowledgeUpsertRequest request,
                                                     Long operatorId,
                                                     boolean enforceOwnership,
                                                     boolean isAdmin) {
        validateRequest(request);

        KnowledgeDocument document;
        if (documentId == null) {
            document = new KnowledgeDocument();
            document.setCreatedBy(operatorId);
            document.setVersionNo(1);
        } else {
            document = documentRepository.findById(documentId)
                    .orElseThrow(() -> new RagException("閻儴鐦戦弬鍥ㄣ€傛稉宥呯摠閸? " + documentId));
            if (enforceOwnership) {
                assertCanManageDocument(document, operatorId, isAdmin);
            }
            cleanupDocumentChunks(document.getId());
            document.setVersionNo(document.getVersionNo() + 1);
        }

        document.setTitle(request.title().trim());
        document.setContent(request.content().trim());
        document.setSourceType(trimToNull(request.sourceType()));
        document.setSourceRef(trimToNull(request.sourceRef()));
        document.setStatus(STATUS_ACTIVE);

        KnowledgeDocument savedDocument = documentRepository.save(document);
        List<String> chunks = textChunkingService.splitToChunks(savedDocument.getContent());
        if (chunks.isEmpty()) {
            throw new RagException("知识内容未生成有效分片。");
        }

        List<KnowledgeChunk> chunkEntities = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String chunkContent = chunks.get(i);
            String pointId = buildPointId(savedDocument.getId(), savedDocument.getVersionNo(), i);
            Map<String, Object> payload = buildPointPayload(savedDocument, i, chunkContent);
            vectorStoreClient.upsert(pointId, embeddingService.vectorize(chunkContent), payload);

            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setDocumentId(savedDocument.getId());
            chunk.setChunkIndex(i);
            chunk.setContent(chunkContent);
            chunk.setPointId(pointId);
            chunkEntities.add(chunk);
        }
        chunkRepository.saveAll(chunkEntities);
        return new KnowledgeDocumentResponse(
                savedDocument.getId(),
                savedDocument.getTitle(),
                savedDocument.getSourceType(),
                savedDocument.getSourceRef(),
                savedDocument.getStatus(),
                savedDocument.getVersionNo(),
                chunkEntities.size(),
                savedDocument.getUpdatedAt()
        );
    }

    private void validateRequest(KnowledgeUpsertRequest request) {
        if (request == null) {
            throw new RagException("知识文档请求不能为空。");
        }
        if (request.title() == null || request.title().isBlank()) {
            throw new RagException("知识标题不能为空。");
        }
        if (request.content() == null || request.content().isBlank()) {
            throw new RagException("知识内容不能为空。");
        }
    }

    private void assertCanManageDocument(KnowledgeDocument document, Long operatorId, boolean isAdmin) {
        if (isAdmin) {
            return;
        }
        if (operatorId == null) {
            throw new RagException("当前用户无权限操作该知识文档。");
        }
        if (!Objects.equals(operatorId, document.getCreatedBy())) {
            throw new RagException("当前用户无权限操作该知识文档。");
        }
    }

    private boolean isVisibleToUser(KnowledgeDocument document, Long operatorId, boolean isAdmin) {
        if (isAdmin) {
            return true;
        }
        if (operatorId == null) {
            return false;
        }
        Long createdBy = document.getCreatedBy();
        return createdBy != null && Objects.equals(createdBy, operatorId);
    }

    private void cleanupDocumentChunks(Long documentId) {
        List<KnowledgeChunk> oldChunks = chunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);
        if (!oldChunks.isEmpty()) {
            vectorStoreClient.deletePoints(oldChunks.stream().map(KnowledgeChunk::getPointId).toList());
            chunkRepository.deleteByDocumentId(documentId);
        }
    }

    private KnowledgeDocumentResponse toDocumentResponse(KnowledgeDocument document) {
        return new KnowledgeDocumentResponse(
                document.getId(),
                document.getTitle(),
                document.getSourceType(),
                document.getSourceRef(),
                document.getStatus(),
                document.getVersionNo(),
                (int) chunkRepository.countByDocumentId(document.getId()),
                document.getUpdatedAt()
        );
    }

    /**
     * 转换为知识文档详情响应对象。
     */
    private KnowledgeDocumentDetailResponse toDocumentDetailResponse(KnowledgeDocument document) {
        return new KnowledgeDocumentDetailResponse(
                document.getId(),
                document.getTitle(),
                document.getContent(),
                document.getSourceType(),
                document.getSourceRef(),
                document.getStatus(),
                document.getVersionNo(),
                (int) chunkRepository.countByDocumentId(document.getId()),
                document.getUpdatedAt()
        );
    }

    private Map<String, Object> buildPointPayload(KnowledgeDocument document, int chunkIndex, String chunkContent) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("documentId", document.getId());
        payload.put("documentTitle", document.getTitle());
        payload.put("sourceType", document.getSourceType());
        payload.put("sourceRef", document.getSourceRef());
        payload.put("createdBy", document.getCreatedBy());
        payload.put("versionNo", document.getVersionNo());
        payload.put("chunkIndex", chunkIndex);
        payload.put("snippet", shortSnippet(chunkContent));
        return payload;
    }

    private String buildPointId(Long documentId, int versionNo, int chunkIndex) {
        return UUID.randomUUID().toString();
    }

    private String shortSnippet(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String normalized = content.trim().replace('\n', ' ');
        return normalized.length() <= 180 ? normalized : normalized.substring(0, 180) + "...";
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String normalizeTitle(String title, String originalFilename) {
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        if (originalFilename != null && !originalFilename.isBlank()) {
            String filename = originalFilename.trim();
            int slash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
            String baseName = slash >= 0 ? filename.substring(slash + 1) : filename;
            int dot = baseName.lastIndexOf('.');
            if (dot > 0) {
                baseName = baseName.substring(0, dot);
            }
            if (!baseName.isBlank()) {
                return baseName.trim();
            }
        }
        return "上传知识文档";
    }

    private String normalizeSourceRef(String sourceRef, String originalFilename) {
        if (sourceRef != null && !sourceRef.isBlank()) {
            return sourceRef.trim();
        }
        if (originalFilename != null && !originalFilename.isBlank()) {
            return originalFilename.trim();
        }
        return null;
    }

    private List<PopularAttractionSeedItem> loadPopularAttractionSeeds() {
        Resource resource = resourceLoader.getResource(POPULAR_ATTRACTIONS_RESOURCE);
        if (!resource.exists()) {
            throw new RagException("閻戭參妫弲顖滃仯閻儴鐦戞惔鎾茨侀弶澶哥瑝鐎涙ê婀? " + POPULAR_ATTRACTIONS_RESOURCE);
        }
        try {
            List<PopularAttractionSeedItem> seeds = objectMapper.readValue(
                    resource.getInputStream(),
                    new TypeReference<>() {
                    }
            );
            return seeds.stream()
                    .filter(Objects::nonNull)
                    .map(PopularAttractionSeedItem::sanitize)
                    .filter(PopularAttractionSeedItem::isValid)
                    .toList();
        } catch (IOException ex) {
            throw new RagException("加载热门景点知识库模板失败。");
        }
    }

    private record PopularAttractionSeedItem(String slug, String title, String content) {
        private PopularAttractionSeedItem sanitize() {
            return new PopularAttractionSeedItem(
                    slug == null ? null : slug.trim(),
                    title == null ? null : title.trim(),
                    content == null ? null : content.trim()
            );
        }

        private boolean isValid() {
            return slug != null && !slug.isBlank()
                    && title != null && !title.isBlank()
                    && content != null && !content.isBlank();
        }
    }
}
