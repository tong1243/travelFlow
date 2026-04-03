package com.example.demo.assistant;

import com.example.demo.assistant.dto.PortalCategoryQueryResponse;
import com.example.demo.assistant.dto.PortalEnterpriseItem;
import com.example.demo.assistant.dto.PortalGuideItem;
import com.example.demo.assistant.dto.PortalHomeResponse;
import com.example.demo.assistant.dto.PortalSlideItem;
import com.example.demo.assistant.dto.PortalSpotItem;
import com.example.demo.assistant.repo.PortalCategoryRepository;
import com.example.demo.assistant.repo.PortalEnterpriseCardRepository;
import com.example.demo.assistant.repo.PortalGuideCardRepository;
import com.example.demo.assistant.repo.PortalNavItemRepository;
import com.example.demo.assistant.repo.PortalSlideRepository;
import com.example.demo.assistant.repo.PortalSpotCardRepository;
import com.example.demo.assistant.repo.PortalSuggestionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PortalContentService {

    private final PortalNavItemRepository navItemRepository;
    private final PortalCategoryRepository categoryRepository;
    private final PortalSuggestionRepository suggestionRepository;
    private final PortalSlideRepository slideRepository;
    private final PortalSpotCardRepository spotCardRepository;
    private final PortalGuideCardRepository guideCardRepository;
    private final PortalEnterpriseCardRepository enterpriseCardRepository;

    public PortalContentService(PortalNavItemRepository navItemRepository,
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

    public PortalHomeResponse home() {
        List<String> navItems = navItemRepository.findByEnabledTrueOrderBySortOrderAscIdAsc().stream()
                .map(item -> item.getLabel().trim())
                .toList();
        List<String> categories = categoryRepository.findByEnabledTrueOrderBySortOrderAscIdAsc().stream()
                .map(item -> item.getName().trim())
                .toList();
        List<String> suggestions = suggestionRepository.findByEnabledTrueOrderBySortOrderAscIdAsc().stream()
                .map(item -> item.getValue().trim())
                .toList();
        List<PortalSlideItem> slides = slideRepository.findByEnabledTrueOrderBySortOrderAscIdAsc().stream()
                .map(item -> new PortalSlideItem(
                        item.getTitle(),
                        item.getSubtitle(),
                        item.getDescription(),
                        item.getImage()
                ))
                .toList();
        List<PortalSpotItem> spots = mapSpotItems();
        List<PortalGuideItem> guides = mapGuideItems();
        List<PortalEnterpriseItem> enterpriseCards = enterpriseCardRepository.findByEnabledTrueOrderBySortOrderAscIdAsc().stream()
                .map(item -> new PortalEnterpriseItem(item.getTitle(), item.getDescription()))
                .toList();
        return new PortalHomeResponse(navItems, categories, suggestions, slides, spots, guides, enterpriseCards);
    }

    public List<String> suggest(String keyword) {
        List<String> suggestions = suggestionRepository.findByEnabledTrueOrderBySortOrderAscIdAsc().stream()
                .map(item -> item.getValue().trim())
                .toList();
        String text = keyword == null ? "" : keyword.trim().toLowerCase();
        if (text.isBlank()) {
            return suggestions.stream().limit(6).toList();
        }
        return suggestions.stream()
                .filter(item -> item.toLowerCase().contains(text))
                .limit(6)
                .toList();
    }

    public List<PortalSpotItem> spots() {
        return mapSpotItems();
    }

    public List<PortalGuideItem> guides() {
        return mapGuideItems();
    }

    public List<String> categories() {
        return categoryRepository.findByEnabledTrueOrderBySortOrderAscIdAsc().stream()
                .map(item -> item.getName().trim())
                .toList();
    }

    public PortalCategoryQueryResponse categoryQuery(String category) {
        String name = category == null ? "" : category.trim();
        String keyword = categoryRepository.findByNameIgnoreCaseAndEnabledTrue(name)
                .map(item -> item.getKeyword().trim())
                .orElse("热门旅行路线推荐");
        return new PortalCategoryQueryResponse(name, keyword);
    }

    private List<PortalSpotItem> mapSpotItems() {
        return spotCardRepository.findByEnabledTrueOrderBySortOrderAscIdAsc().stream()
                .map(item -> new PortalSpotItem(
                        item.getTitle(),
                        item.getLocation(),
                        item.getPrice(),
                        item.getRating(),
                        item.getImage()
                ))
                .toList();
    }

    private List<PortalGuideItem> mapGuideItems() {
        return guideCardRepository.findByEnabledTrueOrderBySortOrderAscIdAsc().stream()
                .map(item -> new PortalGuideItem(
                        item.getTitle(),
                        item.getCover(),
                        item.getReads()
                ))
                .toList();
    }
}
