package com.example.demo.assistant.repo;

import com.example.demo.assistant.entity.PortalNavItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PortalNavItemRepository extends JpaRepository<PortalNavItemEntity, Long> {

    List<PortalNavItemEntity> findByEnabledTrueOrderBySortOrderAscIdAsc();

    List<PortalNavItemEntity> findAllByOrderBySortOrderAscIdAsc();
}
