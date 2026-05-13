package com.example.demo.rag.controller;

import com.example.demo.rag.dto.VectorSearchRequest;
import com.example.demo.rag.dto.VectorSearchResponse;
import com.example.demo.rag.dto.VectorUpsertRequest;
import com.example.demo.rag.dto.VectorizeRequest;
import com.example.demo.rag.dto.VectorizeResponse;
import com.example.demo.rag.service.VectorService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/vector")
@Deprecated(since = "2026-04", forRemoval = false)
/**
 * VectorController类。
 * 该类型负责接收并处理接口请求，协调服务层完成业务响应。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 */
public class VectorController {

    private final VectorService vectorService;

    /**
     * 构造并初始化 VectorController 对象。
     * 该构造方法用于注入运行所需依赖，保证对象创建后即可参与完整流程。
     * 该方法位于控制层，负责参数承接、上下文透传和响应封装。
     * @param vectorService 输入参数 vectorService，用于参与本次处理流程。
     */
    public VectorController(VectorService vectorService) {
        this.vectorService = vectorService;
    }

    @PostMapping("/embed")
    /**
     * 执行 embed 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于控制层，负责参数承接、上下文透传和响应封装。
     * @param request 输入参数 request，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public VectorizeResponse embed(@Valid @RequestBody VectorizeRequest request) {
        return vectorService.vectorize(request.text());
    }

    @PostMapping("/search")
    /**
     * 执行 search 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于控制层，负责参数承接、上下文透传和响应封装。
     * @param request 输入参数 request，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public VectorSearchResponse search(@Valid @RequestBody VectorSearchRequest request) {
        return vectorService.search(request);
    }

    @PostMapping("/upsert")
    /**
     * 执行 upsert 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于控制层，负责参数承接、上下文透传和响应封装。
     * @param request 输入参数 request，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public Map<String, String> upsert(@Valid @RequestBody VectorUpsertRequest request) {
        String pointId = vectorService.upsert(request);
        return Map.of("pointId", pointId);
    }
}
