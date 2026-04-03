package com.example.demo.assistant.repo;

import com.example.demo.assistant.entity.PortalSuggestionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PortalSuggestionRepository extends JpaRepository<PortalSuggestionEntity, Long> {

    List<PortalSuggestionEntity> findByEnabledTrueOrderBySortOrderAscIdAsc();

    List<PortalSuggestionEntity> findAllByOrderBySortOrderAscIdAsc();
}
