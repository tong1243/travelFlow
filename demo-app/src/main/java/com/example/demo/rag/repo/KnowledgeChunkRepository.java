package com.example.demo.rag.repo;

import com.example.demo.rag.entity.KnowledgeChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, Long> {

    List<KnowledgeChunk> findByDocumentIdOrderByChunkIndexAsc(Long documentId);

    List<KnowledgeChunk> findByPointIdIn(List<String> pointIds);

    long countByDocumentId(Long documentId);

    void deleteByDocumentId(Long documentId);
}
