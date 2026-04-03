package com.example.demo.assistant.repo;

import com.example.demo.assistant.entity.PortalGuideCardEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PortalGuideCardRepository extends JpaRepository<PortalGuideCardEntity, Long> {

    List<PortalGuideCardEntity> findByEnabledTrueOrderBySortOrderAscIdAsc();

    List<PortalGuideCardEntity> findAllByOrderBySortOrderAscIdAsc();
}
