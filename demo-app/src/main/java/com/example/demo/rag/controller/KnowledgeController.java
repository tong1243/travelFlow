package com.example.demo.rag.controller;

import com.example.demo.rag.dto.KnowledgeDocumentResponse;
import com.example.demo.rag.dto.KnowledgeUpsertRequest;
import com.example.demo.rag.security.AuthenticatedUser;
import com.example.demo.rag.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeController {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @PostMapping("/documents")
    @Deprecated(since = "2026-04", forRemoval = false)
    public KnowledgeDocumentResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                                            @Valid @RequestBody KnowledgeUpsertRequest request) {
        return knowledgeBaseService.createDocument(request, user.getId());
    }

    @PutMapping("/documents/{documentId}")
    @Deprecated(since = "2026-04", forRemoval = false)
    public KnowledgeDocumentResponse update(@AuthenticationPrincipal AuthenticatedUser user,
                                            @PathVariable("documentId") Long documentId,
                                            @Valid @RequestBody KnowledgeUpsertRequest request) {
        return knowledgeBaseService.updateDocument(documentId, request, user.getId());
    }

    @PostMapping(value = "/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Deprecated(since = "2026-04", forRemoval = false)
    public KnowledgeDocumentResponse upload(@AuthenticationPrincipal AuthenticatedUser user,
                                            @RequestPart("file") MultipartFile file,
                                            @RequestParam(value = "title", required = false) String title,
                                            @RequestParam(value = "sourceType", required = false) String sourceType,
                                            @RequestParam(value = "sourceRef", required = false) String sourceRef) throws IOException {
        KnowledgeUpsertRequest request = knowledgeBaseService.parseUploadToRequest(title, sourceType, sourceRef, file.getBytes());
        return knowledgeBaseService.createDocument(request, user.getId());
    }

    @GetMapping("/documents")
    @Deprecated(since = "2026-04", forRemoval = false)
    public List<KnowledgeDocumentResponse> list() {
        return knowledgeBaseService.listDocuments();
    }

    @DeleteMapping("/documents/{documentId}")
    @Deprecated(since = "2026-04", forRemoval = false)
    public void delete(@PathVariable("documentId") Long documentId) {
        knowledgeBaseService.deleteDocument(documentId);
    }
}
