package com.example.demo.rag.repo;

import com.example.demo.rag.entity.KnowledgeDocument;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {
}
