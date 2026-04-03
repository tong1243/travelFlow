package com.example.demo.assistant;

import com.example.demo.assistant.dto.AdminCategoryRequest;
import com.example.demo.assistant.dto.AdminCategoryResponse;
import com.example.demo.assistant.dto.AdminEnterpriseCardRequest;
import com.example.demo.assistant.dto.AdminEnterpriseCardResponse;
import com.example.demo.assistant.dto.AdminGuideCardRequest;
import com.example.demo.assistant.dto.AdminGuideCardResponse;
import com.example.demo.assistant.dto.AdminNavItemRequest;
import com.example.demo.assistant.dto.AdminNavItemResponse;
import com.example.demo.assistant.dto.AdminSlideRequest;
import com.example.demo.assistant.dto.AdminSlideResponse;
import com.example.demo.assistant.dto.AdminSpotCardRequest;
import com.example.demo.assistant.dto.AdminSpotCardResponse;
import com.example.demo.assistant.dto.AdminSuggestionRequest;
import com.example.demo.assistant.dto.AdminSuggestionResponse;
import com.example.demo.assistant.entity.PortalCategoryEntity;
import com.example.demo.assistant.entity.PortalEnterpriseCardEntity;
import com.example.demo.assistant.entity.PortalGuideCardEntity;
import com.example.demo.assistant.entity.PortalNavItemEntity;
import com.example.demo.assistant.entity.PortalSlideEntity;
import com.example.demo.assistant.entity.PortalSpotCardEntity;
import com.example.demo.assistant.entity.PortalSuggestionEntity;
import com.example.demo.assistant.repo.PortalCategoryRepository;
import com.example.demo.assistant.repo.PortalEnterpriseCardRepository;
import com.example.demo.assistant.repo.PortalGuideCardRepository;
import com.example.demo.assistant.repo.PortalNavItemRepository;
import com.example.demo.assistant.repo.PortalSlideRepository;
import com.example.demo.assistant.repo.PortalSpotCardRepository;
import com.example.demo.assistant.repo.PortalSuggestionRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/admin/portal")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPortalController {

    private final PortalNavItemRepository navItemRepository;
    private final PortalCategoryRepository categoryRepository;
    private final PortalSuggestionRepository suggestionRepository;
    private final PortalSlideRepository slideRepository;
    private final PortalSpotCardRepository spotCardRepository;
    private final PortalGuideCardRepository guideCardRepository;
    private final PortalEnterpriseCardRepository enterpriseCardRepository;

    public AdminPortalController(PortalNavItemRepository navItemRepository,
                                 PortalCategoryRepository categoryRepository,
                                 PortalSuggestionRepository suggestionRepository,
                                 PortalSlideRepository slideRepository,
                                 PortalSpotCardRepository spotCardRepository,
                                 PortalGuideCardRepository guideCardRepository,
                                 PortalEnterpriseCardRepository enterpriseCardRepository) {
        this.navItemRepository = navItemRepository;
        this.categoryRepository = categoryRepository;
        this.suggestionRepository = suggestionRepository;
        this.slideRepository = slideRepository;
        this.spotCardRepository = spotCardRepository;
        this.guideCardRepository = guideCardRepository;
        this.enterpriseCardRepository = enterpriseCardRepository;
    }

    @GetMapping("/nav-items")
    public List<AdminNavItemResponse> listNavItems() {
        return navItemRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(this::toNavResponse)
                .toList();
    }

    @PostMapping("/nav-items")
    public AdminNavItemResponse createNavItem(@Valid @RequestBody AdminNavItemRequest request) {
        PortalNavItemEntity entity = new PortalNavItemEntity();
        applyNav(entity, request);
        return toNavResponse(navItemRepository.save(entity));
    }

    @PutMapping("/nav-items/{id}")
    public AdminNavItemResponse updateNavItem(@PathVariable("id") Long id, @Valid @RequestBody AdminNavItemRequest request) {
        PortalNavItemEntity entity = navItemRepository.findById(id)
                .orElseThrow(() -> notFound("导航项不存在: " + id));
        applyNav(entity, request);
        return toNavResponse(navItemRepository.save(entity));
    }

    @DeleteMapping("/nav-items/{id}")
    public void deleteNavItem(@PathVariable("id") Long id) {
        if (!navItemRepository.existsById(id)) {
            throw notFound("导航项不存在: " + id);
        }
        navItemRepository.deleteById(id);
    }

    @GetMapping("/categories")
    public List<AdminCategoryResponse> listCategories() {
        return categoryRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(this::toCategoryResponse)
                .toList();
    }

    @PostMapping("/categories")
    public AdminCategoryResponse createCategory(@Valid @RequestBody AdminCategoryRequest request) {
        PortalCategoryEntity entity = new PortalCategoryEntity();
        applyCategory(entity, request);
        return toCategoryResponse(categoryRepository.save(entity));
    }

    @PutMapping("/categories/{id}")
    public AdminCategoryResponse updateCategory(@PathVariable("id") Long id, @Valid @RequestBody AdminCategoryRequest request) {
        PortalCategoryEntity entity = categoryRepository.findById(id)
                .orElseThrow(() -> notFound("分类不存在: " + id));
        applyCategory(entity, request);
        return toCategoryResponse(categoryRepository.save(entity));
    }

    @DeleteMapping("/categories/{id}")
    public void deleteCategory(@PathVariable("id") Long id) {
        if (!categoryRepository.existsById(id)) {
            throw notFound("分类不存在: " + id);
        }
        categoryRepository.deleteById(id);
    }

    @GetMapping("/suggestions")
    public List<AdminSuggestionResponse> listSuggestions() {
        return suggestionRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(this::toSuggestionResponse)
                .toList();
    }

    @PostMapping("/suggestions")
    public AdminSuggestionResponse createSuggestion(@Valid @RequestBody AdminSuggestionRequest request) {
        PortalSuggestionEntity entity = new PortalSuggestionEntity();
        applySuggestion(entity, request);
        return toSuggestionResponse(suggestionRepository.save(entity));
    }

    @PutMapping("/suggestions/{id}")
    public AdminSuggestionResponse updateSuggestion(@PathVariable("id") Long id,
                                                    @Valid @RequestBody AdminSuggestionRequest request) {
        PortalSuggestionEntity entity = suggestionRepository.findById(id)
                .orElseThrow(() -> notFound("建议词不存在: " + id));
        applySuggestion(entity, request);
        return toSuggestionResponse(suggestionRepository.save(entity));
    }

    @DeleteMapping("/suggestions/{id}")
    public void deleteSuggestion(@PathVariable("id") Long id) {
        if (!suggestionRepository.existsById(id)) {
            throw notFound("建议词不存在: " + id);
        }
        suggestionRepository.deleteById(id);
    }

    @GetMapping("/slides")
    public List<AdminSlideResponse> listSlides() {
        return slideRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(this::toSlideResponse)
                .toList();
    }

    @PostMapping("/slides")
    public AdminSlideResponse createSlide(@Valid @RequestBody AdminSlideRequest request) {
        PortalSlideEntity entity = new PortalSlideEntity();
        applySlide(entity, request);
        return toSlideResponse(slideRepository.save(entity));
    }

    @PutMapping("/slides/{id}")
    public AdminSlideResponse updateSlide(@PathVariable("id") Long id, @Valid @RequestBody AdminSlideRequest request) {
        PortalSlideEntity entity = slideRepository.findById(id)
                .orElseThrow(() -> notFound("轮播卡片不存在: " + id));
        applySlide(entity, request);
        return toSlideResponse(slideRepository.save(entity));
    }

    @DeleteMapping("/slides/{id}")
    public void deleteSlide(@PathVariable("id") Long id) {
        if (!slideRepository.existsById(id)) {
            throw notFound("轮播卡片不存在: " + id);
        }
        slideRepository.deleteById(id);
    }

    @GetMapping("/spots")
    public List<AdminSpotCardResponse> listSpots() {
        return spotCardRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(this::toSpotResponse)
                .toList();
    }

    @PostMapping("/spots")
    public AdminSpotCardResponse createSpot(@Valid @RequestBody AdminSpotCardRequest request) {
        PortalSpotCardEntity entity = new PortalSpotCardEntity();
        applySpot(entity, request);
        return toSpotResponse(spotCardRepository.save(entity));
    }

    @PutMapping("/spots/{id}")
    public AdminSpotCardResponse updateSpot(@PathVariable("id") Long id, @Valid @RequestBody AdminSpotCardRequest request) {
        PortalSpotCardEntity entity = spotCardRepository.findById(id)
                .orElseThrow(() -> notFound("景点卡片不存在: " + id));
        applySpot(entity, request);
        return toSpotResponse(spotCardRepository.save(entity));
    }

    @DeleteMapping("/spots/{id}")
    public void deleteSpot(@PathVariable("id") Long id) {
        if (!spotCardRepository.existsById(id)) {
            throw notFound("景点卡片不存在: " + id);
        }
        spotCardRepository.deleteById(id);
    }

    @GetMapping("/guides")
    public List<AdminGuideCardResponse> listGuides() {
        return guideCardRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(this::toGuideResponse)
                .toList();
    }

    @PostMapping("/guides")
    public AdminGuideCardResponse createGuide(@Valid @RequestBody AdminGuideCardRequest request) {
        PortalGuideCardEntity entity = new PortalGuideCardEntity();
        applyGuide(entity, request);
        return toGuideResponse(guideCardRepository.save(entity));
    }

    @PutMapping("/guides/{id}")
    public AdminGuideCardResponse updateGuide(@PathVariable("id") Long id, @Valid @RequestBody AdminGuideCardRequest request) {
        PortalGuideCardEntity entity = guideCardRepository.findById(id)
                .orElseThrow(() -> notFound("攻略卡片不存在: " + id));
        applyGuide(entity, request);
        return toGuideResponse(guideCardRepository.save(entity));
    }

    @DeleteMapping("/guides/{id}")
    public void deleteGuide(@PathVariable("id") Long id) {
        if (!guideCardRepository.existsById(id)) {
            throw notFound("攻略卡片不存在: " + id);
        }
        guideCardRepository.deleteById(id);
    }

    @GetMapping("/enterprise-cards")
    public List<AdminEnterpriseCardResponse> listEnterpriseCards() {
        return enterpriseCardRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(this::toEnterpriseResponse)
                .toList();
    }

    @PostMapping("/enterprise-cards")
    public AdminEnterpriseCardResponse createEnterpriseCard(@Valid @RequestBody AdminEnterpriseCardRequest request) {
        PortalEnterpriseCardEntity entity = new PortalEnterpriseCardEntity();
        applyEnterprise(entity, request);
        return toEnterpriseResponse(enterpriseCardRepository.save(entity));
    }

    @PutMapping("/enterprise-cards/{id}")
    public AdminEnterpriseCardResponse updateEnterpriseCard(@PathVariable("id") Long id,
                                                            @Valid @RequestBody AdminEnterpriseCardRequest request) {
        PortalEnterpriseCardEntity entity = enterpriseCardRepository.findById(id)
                .orElseThrow(() -> notFound("企业卡片不存在: " + id));
        applyEnterprise(entity, request);
        return toEnterpriseResponse(enterpriseCardRepository.save(entity));
    }

    @DeleteMapping("/enterprise-cards/{id}")
    public void deleteEnterpriseCard(@PathVariable("id") Long id) {
        if (!enterpriseCardRepository.existsById(id)) {
            throw notFound("企业卡片不存在: " + id);
        }
        enterpriseCardRepository.deleteById(id);
    }

    private void applyNav(PortalNavItemEntity entity, AdminNavItemRequest request) {
        entity.setLabel(request.label().trim());
        entity.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        entity.setEnabled(request.enabled() == null || request.enabled());
    }

    private void applyCategory(PortalCategoryEntity entity, AdminCategoryRequest request) {
        entity.setName(request.name().trim());
        entity.setKeyword(request.keyword().trim());
        entity.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        entity.setEnabled(request.enabled() == null || request.enabled());
    }

    private void applySuggestion(PortalSuggestionEntity entity, AdminSuggestionRequest request) {
        entity.setValue(request.value().trim());
        entity.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        entity.setEnabled(request.enabled() == null || request.enabled());
    }

    private void applySlide(PortalSlideEntity entity, AdminSlideRequest request) {
        entity.setTitle(request.title().trim());
        entity.setSubtitle(request.subtitle().trim());
        entity.setDescription(request.description().trim());
        entity.setImage(request.image().trim());
        entity.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        entity.setEnabled(request.enabled() == null || request.enabled());
    }

    private void applySpot(PortalSpotCardEntity entity, AdminSpotCardRequest request) {
        entity.setTitle(request.title().trim());
        entity.setLocation(request.location().trim());
        entity.setPrice(request.price().trim());
        entity.setRating(request.rating().trim());
        entity.setImage(request.image().trim());
        entity.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        entity.setEnabled(request.enabled() == null || request.enabled());
    }

    private void applyGuide(PortalGuideCardEntity entity, AdminGuideCardRequest request) {
        entity.setTitle(request.title().trim());
        entity.setCover(request.cover().trim());
        entity.setReads(request.reads().trim());
        entity.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        entity.setEnabled(request.enabled() == null || request.enabled());
    }

    private void applyEnterprise(PortalEnterpriseCardEntity entity, AdminEnterpriseCardRequest request) {
        entity.setTitle(request.title().trim());
        entity.setDescription(request.description().trim());
        entity.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        entity.setEnabled(request.enabled() == null || request.enabled());
    }

    private AdminNavItemResponse toNavResponse(PortalNavItemEntity item) {
        return new AdminNavItemResponse(item.getId(), item.getLabel(), item.getSortOrder(), item.isEnabled());
    }

    private AdminCategoryResponse toCategoryResponse(PortalCategoryEntity item) {
        return new AdminCategoryResponse(item.getId(), item.getName(), item.getKeyword(), item.getSortOrder(), item.isEnabled());
    }

    private AdminSuggestionResponse toSuggestionResponse(PortalSuggestionEntity item) {
        return new AdminSuggestionResponse(item.getId(), item.getValue(), item.getSortOrder(), item.isEnabled());
    }

    private AdminSlideResponse toSlideResponse(PortalSlideEntity item) {
        return new AdminSlideResponse(
                item.getId(),
                item.getTitle(),
                item.getSubtitle(),
                item.getDescription(),
                item.getImage(),
                item.getSortOrder(),
                item.isEnabled()
        );
    }

    private AdminSpotCardResponse toSpotResponse(PortalSpotCardEntity item) {
        return new AdminSpotCardResponse(
                item.getId(),
                item.getTitle(),
                item.getLocation(),
                item.getPrice(),
                item.getRating(),
                item.getImage(),
                item.getSortOrder(),
                item.isEnabled()
        );
    }

    private AdminGuideCardResponse toGuideResponse(PortalGuideCardEntity item) {
        return new AdminGuideCardResponse(
                item.getId(),
                item.getTitle(),
                item.getCover(),
                item.getReads(),
                item.getSortOrder(),
                item.isEnabled()
        );
    }

    private AdminEnterpriseCardResponse toEnterpriseResponse(PortalEnterpriseCardEntity item) {
        return new AdminEnterpriseCardResponse(
                item.getId(),
                item.getTitle(),
                item.getDescription(),
                item.getSortOrder(),
                item.isEnabled()
        );
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}

