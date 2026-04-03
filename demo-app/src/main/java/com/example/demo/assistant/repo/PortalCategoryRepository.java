package com.example.demo.assistant.repo;

import com.example.demo.assistant.entity.PortalCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PortalCategoryRepository extends JpaRepository<PortalCategoryEntity, Long> {

    List<PortalCategoryEntity> findByEnabledTrueOrderBySortOrderAscIdAsc();

    List<PortalCategoryEntity> findAllByOrderBySortOrderAscIdAsc();

    Optional<PortalCategoryEntity> findByNameIgnoreCaseAndEnabledTrue(String name);
}
