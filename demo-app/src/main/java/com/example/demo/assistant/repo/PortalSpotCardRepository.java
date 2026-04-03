package com.example.demo.assistant.repo;

import com.example.demo.assistant.entity.PortalSpotCardEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PortalSpotCardRepository extends JpaRepository<PortalSpotCardEntity, Long> {

    List<PortalSpotCardEntity> findByEnabledTrueOrderBySortOrderAscIdAsc();

    List<PortalSpotCardEntity> findAllByOrderBySortOrderAscIdAsc();
}
