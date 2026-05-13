package com.example.demo.rag.controller;

import com.example.demo.rag.dto.TripPlanResponse;
import com.example.demo.rag.dto.TripPlanUpsertRequest;
import com.example.demo.rag.security.AuthenticatedUser;
import com.example.demo.rag.service.TripPlanService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/trips")
/**
 * TripPlanController类。
 * 该类型负责接收并处理接口请求，协调服务层完成业务响应。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 */
public class TripPlanController {

    private final TripPlanService tripPlanService;

    /**
     * 构造并初始化 TripPlanController 对象。
     * 该构造方法用于注入运行所需依赖，保证对象创建后即可参与完整流程。
     * 该方法位于控制层，负责参数承接、上下文透传和响应封装。
     * @param tripPlanService 输入参数 tripPlanService，用于参与本次处理流程。
     */
    public TripPlanController(TripPlanService tripPlanService) {
        this.tripPlanService = tripPlanService;
    }

    @GetMapping
    /**
     * 执行 list 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于控制层，负责参数承接、上下文透传和响应封装。
     * @param user 输入参数 user，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public List<TripPlanResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return tripPlanService.listByUser(user.getId());
    }

    @GetMapping("/{id}")
    /**
     * 执行 detail 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于控制层，负责参数承接、上下文透传和响应封装。
     * @param user 输入参数 user，用于参与本次处理流程。
     * @param id 输入参数 id，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public TripPlanResponse detail(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable("id") Long id) {
        return tripPlanService.getById(user.getId(), id);
    }

    @PostMapping
    /**
     * 执行 create 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于控制层，负责参数承接、上下文透传和响应封装。
     * @param user 输入参数 user，用于参与本次处理流程。
     * @param request 输入参数 request，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public TripPlanResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                                   @Valid @RequestBody TripPlanUpsertRequest request) {
        return tripPlanService.create(user.getId(), request);
    }

    @PutMapping("/{id}")
    /**
     * 执行 update 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于控制层，负责参数承接、上下文透传和响应封装。
     * @param user 输入参数 user，用于参与本次处理流程。
     * @param id 输入参数 id，用于参与本次处理流程。
     * @param request 输入参数 request，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public TripPlanResponse update(@AuthenticationPrincipal AuthenticatedUser user,
                                   @PathVariable("id") Long id,
                                   @Valid @RequestBody TripPlanUpsertRequest request) {
        return tripPlanService.update(user.getId(), id, request);
    }

    @DeleteMapping("/{id}")
    /**
     * 执行 delete 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于控制层，负责参数承接、上下文透传和响应封装。
     * @param user 输入参数 user，用于参与本次处理流程。
     * @param id 输入参数 id，用于参与本次处理流程。
     */
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable("id") Long id) {
        tripPlanService.delete(user.getId(), id);
    }
}
