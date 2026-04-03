package com.example.demo.rag.repo;

import com.example.demo.rag.entity.KnowledgeDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

    List<KnowledgeDocument> findByStatusOrderByUpdatedAtDesc(String status);
}
