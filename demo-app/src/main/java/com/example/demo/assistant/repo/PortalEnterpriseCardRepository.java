package com.example.demo.assistant.repo;

import com.example.demo.assistant.entity.PortalEnterpriseCardEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PortalEnterpriseCardRepository extends JpaRepository<PortalEnterpriseCardEntity, Long> {

    List<PortalEnterpriseCardEntity> findByEnabledTrueOrderBySortOrderAscIdAsc();

    List<PortalEnterpriseCardEntity> findAllByOrderBySortOrderAscIdAsc();
}
