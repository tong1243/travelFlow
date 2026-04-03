package com.example.demo.rag.service;

import com.example.demo.rag.RagException;
import com.example.demo.rag.dto.KnowledgeDocumentResponse;
import com.example.demo.rag.dto.KnowledgeUpsertRequest;
import com.example.demo.rag.dto.RagReferenceItem;
import com.example.demo.rag.entity.KnowledgeChunk;
import com.example.demo.rag.entity.KnowledgeDocument;
import com.example.demo.rag.model.HybridSearchHit;
import com.example.demo.rag.model.VectorSearchHit;
import com.example.demo.rag.repo.KnowledgeChunkRepository;
import com.example.demo.rag.repo.KnowledgeDocumentRepository;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class KnowledgeBaseService {

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final TextChunkingService textChunkingService;
    private final EmbeddingService embeddingService;
    private final QdrantVectorStoreClient vectorStoreClient;

    public KnowledgeBaseService(KnowledgeDocumentRepository documentRepository,
                                KnowledgeChunkRepository chunkRepository,
                                TextChunkingService textChunkingService,
                                EmbeddingService embeddingService,
                                QdrantVectorStoreClient vectorStoreClient) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.textChunkingService = textChunkingService;
        this.embeddingService = embeddingService;
        this.vectorStoreClient = vectorStoreClient;
    }

    @Transactional
    public KnowledgeDocumentResponse createDocument(KnowledgeUpsertRequest request, Long operatorId) {
        return upsertInternal(null, request, operatorId);
    }

    @Transactional
    public KnowledgeDocumentResponse updateDocument(Long documentId, KnowledgeUpsertRequest request, Long operatorId) {
        return upsertInternal(documentId, request, operatorId);
    }

    @Transactional
    public void deleteDocument(Long documentId) {
        KnowledgeDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RagException("Knowledge document not found: " + documentId));

        List<KnowledgeChunk> chunks = chunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);
        List<String> pointIds = chunks.stream().map(KnowledgeChunk::getPointId).toList();
        vectorStoreClient.deletePoints(pointIds);
        chunkRepository.deleteByDocumentId(documentId);
        documentRepository.delete(document);
    }

    public List<KnowledgeDocumentResponse> listDocuments() {
        List<KnowledgeDocument> documents = documentRepository.findAll(Sort.by(Sort.Direction.DESC, "updatedAt"));
        return documents.stream()
                .map(this::toDocumentResponse)
                .toList();
    }

    public KnowledgeUpsertRequest parseUploadToRequest(String title, String sourceType, String sourceRef, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new RagException("Uploaded knowledge file is empty.");
        }
        String content = new String(bytes, StandardCharsets.UTF_8).trim();
        if (content.isBlank()) {
            throw new RagException("Uploaded knowledge file has no readable text content.");
        }
        String safeTitle = (title == null || title.isBlank()) ? "Uploaded Knowledge" : title.trim();
        return new KnowledgeUpsertRequest(safeTitle, content, sourceType, sourceRef);
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
            String title = document == null ? "Unknown Document" : document.getTitle();
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

    private KnowledgeDocumentResponse upsertInternal(Long documentId, KnowledgeUpsertRequest request, Long operatorId) {
        if (request.content() == null || request.content().isBlank()) {
            throw new RagException("Knowledge content must not be empty.");
        }

        KnowledgeDocument document;
        if (documentId == null) {
            document = new KnowledgeDocument();
            document.setCreatedBy(operatorId);
            document.setVersionNo(1);
        } else {
            document = documentRepository.findById(documentId)
                    .orElseThrow(() -> new RagException("Knowledge document not found: " + documentId));
            cleanupDocumentChunks(document.getId());
            document.setVersionNo(document.getVersionNo() + 1);
        }

        document.setTitle(request.title().trim());
        document.setContent(request.content().trim());
        document.setSourceType(trimToNull(request.sourceType()));
        document.setSourceRef(trimToNull(request.sourceRef()));
        document.setStatus("ACTIVE");

        KnowledgeDocument savedDocument = documentRepository.save(document);
        List<String> chunks = textChunkingService.splitToChunks(savedDocument.getContent());
        if (chunks.isEmpty()) {
            throw new RagException("No valid chunk generated from knowledge content.");
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

    private Map<String, Object> buildPointPayload(KnowledgeDocument document, int chunkIndex, String chunkContent) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("documentId", document.getId());
        payload.put("documentTitle", document.getTitle());
        payload.put("sourceType", document.getSourceType());
        payload.put("sourceRef", document.getSourceRef());
        payload.put("versionNo", document.getVersionNo());
        payload.put("chunkIndex", chunkIndex);
        payload.put("snippet", shortSnippet(chunkContent));
        return payload;
    }

    private String buildPointId(Long documentId, int versionNo, int chunkIndex) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return "doc-" + documentId + "-v" + versionNo + "-c" + chunkIndex + "-" + suffix;
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
}
