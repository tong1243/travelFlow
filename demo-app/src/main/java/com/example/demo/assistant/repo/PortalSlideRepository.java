package com.example.demo.assistant.repo;

import com.example.demo.assistant.entity.PortalSlideEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PortalSlideRepository extends JpaRepository<PortalSlideEntity, Long> {

    List<PortalSlideEntity> findByEnabledTrueOrderBySortOrderAscIdAsc();

    List<PortalSlideEntity> findAllByOrderBySortOrderAscIdAsc();
}
