package com.example.demo.rag.repo;

import com.example.demo.rag.entity.TripPlanRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TripPlanRecordRepository extends JpaRepository<TripPlanRecord, Long> {

    List<TripPlanRecord> findByUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<TripPlanRecord> findByIdAndUserId(Long id, Long userId);
}
