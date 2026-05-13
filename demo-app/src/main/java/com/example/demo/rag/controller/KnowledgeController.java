package com.example.demo.rag.controller;

import com.example.demo.rag.RagException;
import com.example.demo.rag.dto.KnowledgeDocumentDetailResponse;
import com.example.demo.rag.dto.KnowledgeDocumentResponse;
import com.example.demo.rag.dto.KnowledgeSeedResponse;
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
/**
 * KnowledgeController类。
 * 该类型负责接收并处理接口请求，协调服务层完成业务响应。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 */
public class KnowledgeController {

    private final KnowledgeBaseService knowledgeBaseService;

    /**
     * 构造并初始化 KnowledgeController 对象。
     * 该构造方法用于注入运行所需依赖，保证对象创建后即可参与完整流程。
     * 该方法位于控制层，负责参数承接、上下文透传和响应封装。
     * @param knowledgeBaseService 输入参数 knowledgeBaseService，用于参与本次处理流程。
     */
    public KnowledgeController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @PostMapping("/documents")
    @Deprecated(since = "2026-04", forRemoval = false)
    /**
     * 执行 create 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于控制层，负责参数承接、上下文透传和响应封装。
     * @param user 输入参数 user，用于参与本次处理流程。
     * @param request 输入参数 request，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public KnowledgeDocumentResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                                            @Valid @RequestBody KnowledgeUpsertRequest request) {
        return knowledgeBaseService.createDocument(request, user.getId());
    }

    @PutMapping("/documents/{documentId}")
    @Deprecated(since = "2026-04", forRemoval = false)
    /**
     * 执行 update 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于控制层，负责参数承接、上下文透传和响应封装。
     * @param user 输入参数 user，用于参与本次处理流程。
     * @param documentId 输入参数 documentId，用于参与本次处理流程。
     * @param request 输入参数 request，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public KnowledgeDocumentResponse update(@AuthenticationPrincipal AuthenticatedUser user,
                                            @PathVariable("documentId") Long documentId,
                                            @Valid @RequestBody KnowledgeUpsertRequest request) {
        return knowledgeBaseService.updateDocument(documentId, request, user.getId(), isAdmin(user));
    }

    @PostMapping(value = "/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Deprecated(since = "2026-04", forRemoval = false)
    /**
     * 执行 upload 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于控制层，负责参数承接、上下文透传和响应封装。
     * @param user 输入参数 user，用于参与本次处理流程。
     * @param file 输入参数 file，用于参与本次处理流程。
     * @param title 输入参数 title，用于参与本次处理流程。
     * @param sourceType 输入参数 sourceType，用于参与本次处理流程。
     * @param sourceRef 输入参数 sourceRef，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public KnowledgeDocumentResponse upload(@AuthenticationPrincipal AuthenticatedUser user,
                                            @RequestPart("file") MultipartFile file,
                                            @RequestParam(value = "title", required = false) String title,
                                            @RequestParam(value = "sourceType", required = false) String sourceType,
                                            @RequestParam(value = "sourceRef", required = false) String sourceRef) throws IOException {
        KnowledgeUpsertRequest request = knowledgeBaseService.parseUploadToRequest(
                title,
                sourceType,
                sourceRef,
                file.getOriginalFilename(),
                file.getBytes()
        );
        return knowledgeBaseService.createDocument(request, user.getId());
    }

    @PostMapping("/documents/seed/popular-attractions")
    public KnowledgeSeedResponse seedPopularAttractions(@AuthenticationPrincipal AuthenticatedUser user,
                                                        @RequestParam(value = "overwrite", defaultValue = "false") boolean overwrite) {
        if (!isAdmin(user)) {
            throw new RagException("仅管理员可导入系统热门景点知识库。");
        }
        return knowledgeBaseService.seedPopularAttractions(null, overwrite);
    }

    @GetMapping("/documents")
    @Deprecated(since = "2026-04", forRemoval = false)
    /**
     * 执行 list 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于控制层，负责参数承接、上下文透传和响应封装。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public List<KnowledgeDocumentResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return knowledgeBaseService.listDocuments(user.getId(), isAdmin(user));
    }

    @GetMapping("/documents/{documentId}")
    /**
     * 查询单条知识文档详情。
     * 该接口用于偏好编辑回填，包含文档正文内容。
     *
     * @param user 当前登录用户
     * @param documentId 文档ID
     * @return 文档详情
     */
    public KnowledgeDocumentDetailResponse detail(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @PathVariable("documentId") Long documentId) {
        return knowledgeBaseService.getDocumentDetail(documentId, user.getId(), isAdmin(user));
    }

    @DeleteMapping("/documents/{documentId}")
    @Deprecated(since = "2026-04", forRemoval = false)
    /**
     * 执行 delete 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于控制层，负责参数承接、上下文透传和响应封装。
     * @param documentId 输入参数 documentId，用于参与本次处理流程。
     */
    public void delete(@AuthenticationPrincipal AuthenticatedUser user,
                       @PathVariable("documentId") Long documentId) {
        knowledgeBaseService.deleteDocument(documentId, user.getId(), isAdmin(user));
    }

    private boolean isAdmin(AuthenticatedUser user) {
        return user != null && "ADMIN".equalsIgnoreCase(user.getRole());
    }
}
