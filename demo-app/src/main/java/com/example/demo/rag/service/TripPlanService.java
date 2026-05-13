package com.example.demo.rag.service;

import com.example.demo.rag.RagException;
import com.example.demo.rag.dto.TripPlanResponse;
import com.example.demo.rag.dto.TripPlanUpsertRequest;
import com.example.demo.rag.entity.TripPlanRecord;
import com.example.demo.rag.repo.TripPlanRecordRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
/**
 * TripPlanService类。
 * 该类型负责组织核心业务流程，串联检索、存储与模型调用能力。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 */
public class TripPlanService {

    private final TripPlanRecordRepository tripPlanRecordRepository;

    /**
     * 构造并初始化 TripPlanService 对象。
     * 该构造方法用于注入运行所需依赖，保证对象创建后即可参与完整流程。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param tripPlanRecordRepository 输入参数 tripPlanRecordRepository，用于参与本次处理流程。
     */
    public TripPlanService(TripPlanRecordRepository tripPlanRecordRepository) {
        this.tripPlanRecordRepository = tripPlanRecordRepository;
    }

    /**
     * 执行 listByUser 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param userId 输入参数 userId，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public List<TripPlanResponse> listByUser(Long userId) {
        return tripPlanRecordRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 获取 ById 字段值。
     * 该方法提供只读访问入口，减少调用方直接操作内部状态的风险。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param userId 输入参数 userId，用于参与本次处理流程。
     * @param id 输入参数 id，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public TripPlanResponse getById(Long userId, Long id) {
        TripPlanRecord record = tripPlanRecordRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new RagException("行程不存在或无访问权限"));
        return toResponse(record);
    }

    @Transactional
    /**
     * 执行 create 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param userId 输入参数 userId，用于参与本次处理流程。
     * @param request 输入参数 request，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public TripPlanResponse create(Long userId, TripPlanUpsertRequest request) {
        validateDateRange(request);
        TripPlanRecord record = new TripPlanRecord();
        record.setUserId(userId);
        apply(record, request);
        return toResponse(tripPlanRecordRepository.save(record));
    }

    @Transactional
    /**
     * 执行 update 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param userId 输入参数 userId，用于参与本次处理流程。
     * @param id 输入参数 id，用于参与本次处理流程。
     * @param request 输入参数 request，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public TripPlanResponse update(Long userId, Long id, TripPlanUpsertRequest request) {
        validateDateRange(request);
        TripPlanRecord record = tripPlanRecordRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new RagException("行程不存在或无访问权限"));
        apply(record, request);
        return toResponse(tripPlanRecordRepository.save(record));
    }

    @Transactional
    /**
     * 执行 delete 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param userId 输入参数 userId，用于参与本次处理流程。
     * @param id 输入参数 id，用于参与本次处理流程。
     */
    public void delete(Long userId, Long id) {
        TripPlanRecord record = tripPlanRecordRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new RagException("行程不存在或无访问权限"));
        tripPlanRecordRepository.delete(record);
    }

    /**
     * 执行 validateDateRange 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param request 输入参数 request，用于参与本次处理流程。
     */
    private void validateDateRange(TripPlanUpsertRequest request) {
        if (request.startDate().isAfter(request.endDate())) {
            throw new RagException("开始日期不能晚于结束日期");
        }
    }

    /**
     * 执行 apply 业务处理。
     * 该方法将请求对象中的可更新字段统一拷贝到行程实体中，避免在创建与更新流程中出现重复赋值逻辑。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param record 输入参数 record，用于参与本次处理流程。
     * @param request 输入参数 request，用于参与本次处理流程。
     */
    private void apply(TripPlanRecord record, TripPlanUpsertRequest request) {
        record.setTitle(request.title().trim());
        record.setKeyword(request.keyword().trim());
        record.setSummary(request.summary() == null ? "" : request.summary().trim());
        record.setAnswerText(request.answer().trim());
        record.setDepartureCity(request.departureCity().trim());
        record.setTravelers(request.travelers());
        record.setStartDate(request.startDate());
        record.setEndDate(request.endDate());
        record.setBudget(request.budget().trim());
        record.setCompanionType(request.companionType().trim());
        record.setTravelStyle(request.travelStyle().trim());
    }

    /**
     * 执行 toResponse 业务处理。
     * 该方法负责把持久化实体转换为接口返回对象，隔离数据库字段与外部响应结构，便于后续演进。
     * 该方法位于服务层，负责组织业务步骤并协调上下游依赖。
     * @param record 输入参数 record，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    private TripPlanResponse toResponse(TripPlanRecord record) {
        return new TripPlanResponse(
                record.getId(),
                record.getTitle(),
                record.getKeyword(),
                record.getSummary(),
                record.getAnswerText(),
                record.getDepartureCity(),
                record.getTravelers(),
                record.getStartDate(),
                record.getEndDate(),
                record.getBudget(),
                record.getCompanionType(),
                record.getTravelStyle(),
                record.getCreatedAt(),
                record.getUpdatedAt()
        );
    }
}
