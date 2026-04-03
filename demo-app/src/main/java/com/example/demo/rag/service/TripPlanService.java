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
public class TripPlanService {

    private final TripPlanRecordRepository tripPlanRecordRepository;

    public TripPlanService(TripPlanRecordRepository tripPlanRecordRepository) {
        this.tripPlanRecordRepository = tripPlanRecordRepository;
    }

    public List<TripPlanResponse> listByUser(Long userId) {
        return tripPlanRecordRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    public TripPlanResponse getById(Long userId, Long id) {
        TripPlanRecord record = tripPlanRecordRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new RagException("行程不存在或无访问权限"));
        return toResponse(record);
    }

    @Transactional
    public TripPlanResponse create(Long userId, TripPlanUpsertRequest request) {
        validateDateRange(request);
        TripPlanRecord record = new TripPlanRecord();
        record.setUserId(userId);
        apply(record, request);
        return toResponse(tripPlanRecordRepository.save(record));
    }

    @Transactional
    public TripPlanResponse update(Long userId, Long id, TripPlanUpsertRequest request) {
        validateDateRange(request);
        TripPlanRecord record = tripPlanRecordRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new RagException("行程不存在或无访问权限"));
        apply(record, request);
        return toResponse(tripPlanRecordRepository.save(record));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        TripPlanRecord record = tripPlanRecordRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new RagException("行程不存在或无访问权限"));
        tripPlanRecordRepository.delete(record);
    }

    private void validateDateRange(TripPlanUpsertRequest request) {
        if (request.startDate().isAfter(request.endDate())) {
            throw new RagException("开始日期不能晚于结束日期");
        }
    }

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
