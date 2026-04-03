package com.example.demo.assistant;

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
import com.example.demo.rag.entity.UserAccount;
import com.example.demo.rag.repo.UserAccountRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PortalDataInitializer implements CommandLineRunner {

    private final PortalNavItemRepository navItemRepository;
    private final PortalCategoryRepository categoryRepository;
    private final PortalSuggestionRepository suggestionRepository;
    private final PortalSlideRepository slideRepository;
    private final PortalSpotCardRepository spotCardRepository;
    private final PortalGuideCardRepository guideCardRepository;
    private final PortalEnterpriseCardRepository enterpriseCardRepository;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public PortalDataInitializer(PortalNavItemRepository navItemRepository,
                                 PortalCategoryRepository categoryRepository,
                                 PortalSuggestionRepository suggestionRepository,
                                 PortalSlideRepository slideRepository,
                                 PortalSpotCardRepository spotCardRepository,
                                 PortalGuideCardRepository guideCardRepository,
                                 PortalEnterpriseCardRepository enterpriseCardRepository,
                                 UserAccountRepository userAccountRepository,
                                 PasswordEncoder passwordEncoder) {
        this.navItemRepository = navItemRepository;
        this.categoryRepository = categoryRepository;
        this.suggestionRepository = suggestionRepository;
        this.slideRepository = slideRepository;
        this.spotCardRepository = spotCardRepository;
        this.guideCardRepository = guideCardRepository;
        this.enterpriseCardRepository = enterpriseCardRepository;
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        seedNavItems();
        seedCategories();
        seedSuggestions();
        seedSlides();
        seedSpotCards();
        seedGuideCards();
        seedEnterpriseCards();
        seedAdminUser();
    }

    private void seedNavItems() {
        List<String> labels = List.of("首页", "灵感地图", "精选攻略", "智能助手", "限时特惠", "企业服务");
        List<PortalNavItemEntity> existing = navItemRepository.findAll();
        for (int index = 0; index < labels.size(); index++) {
            String label = labels.get(index);
            PortalNavItemEntity item = existing.stream()
                    .filter(nav -> label.equals(nav.getLabel()))
                    .findFirst()
                    .orElseGet(PortalNavItemEntity::new);
            item.setLabel(label);
            item.setSortOrder(index);
            if (item.getId() == null) {
                item.setEnabled(true);
            }
            navItemRepository.save(item);
        }
    }

    private void seedCategories() {
        if (categoryRepository.count() > 0) {
            return;
        }
        seedCategory(0, "国内游", "国内游热门目的地7天行程");
        seedCategory(1, "出境游", "出境游签证友好国家线路");
        seedCategory(2, "跟团游", "跟团游高性价比路线推荐");
        seedCategory(3, "自由行", "自由行路线优化与避坑建议");
        seedCategory(4, "主题游", "亲子/美食/摄影主题旅行");
    }

    private void seedSuggestions() {
        if (suggestionRepository.count() > 0) {
            return;
        }
        List<String> suggestions = List.of(
                "东京亲子5日线路",
                "京都红叶拍照机位",
                "北海道温泉自由行",
                "新疆环线自驾避坑",
                "云南雨季高性价比玩法",
                "曼谷夜市美食地图",
                "巴黎博物馆通票安排",
                "冰岛极光冬季路线"
        );
        for (int index = 0; index < suggestions.size(); index++) {
            PortalSuggestionEntity item = new PortalSuggestionEntity();
            item.setValue(suggestions.get(index));
            item.setSortOrder(index);
            item.setEnabled(true);
            suggestionRepository.save(item);
        }
    }

    private void seedSlides() {
        if (slideRepository.count() > 0) {
            return;
        }
        seedSlide(
                0,
                "冲绳海岛慢时光",
                "轻松、治愈、适合家庭",
                "蓝洞浮潜 + 海盐餐厅 + 日落海岸线，三天就能快速放松充电。",
                "https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=1600&q=80"
        );
        seedSlide(
                1,
                "京都秋色古都",
                "文化深度、温柔节奏",
                "晨寺夜景、茶屋散步与小众街区并行，经典与体验都兼顾。",
                "https://images.unsplash.com/photo-1492571350019-22de08371fd3?auto=format&fit=crop&w=1600&q=80"
        );
        seedSlide(
                2,
                "瑞士湖山列车",
                "省心高效、景观封顶",
                "火车串联雪山与湖泊，跨城不折腾，适合高质量度假。",
                "https://images.unsplash.com/photo-1527004013197-933c4bb611b3?auto=format&fit=crop&w=1600&q=80"
        );
    }

    private void seedSpotCards() {
        if (spotCardRepository.count() > 0) {
            return;
        }
        seedSpot(
                0,
                "箱根温泉一日疗愈线",
                "日本 · 神奈川",
                "¥899 起",
                "4.9",
                "https://images.unsplash.com/photo-1549693578-d683be217e58?auto=format&fit=crop&w=1000&q=80"
        );
        seedSpot(
                1,
                "阿那亚海岸艺术周末",
                "中国 · 河北",
                "¥699 起",
                "4.8",
                "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=1000&q=80"
        );
        seedSpot(
                2,
                "巴厘岛日落悬崖行程",
                "印尼 · 巴厘岛",
                "¥1599 起",
                "4.9",
                "https://images.unsplash.com/photo-1537996194471-e657df975ab4?auto=format&fit=crop&w=1000&q=80"
        );
        seedSpot(
                3,
                "伊斯坦布尔蓝调漫游",
                "土耳其 · 伊斯坦布尔",
                "¥2399 起",
                "4.7",
                "https://images.unsplash.com/photo-1527838832700-5059252407fa?auto=format&fit=crop&w=1000&q=80"
        );
        seedSpot(
                4,
                "札幌雪国美食巡礼",
                "日本 · 北海道",
                "¥1299 起",
                "4.8",
                "https://images.unsplash.com/photo-1480796927426-f609979314bd?auto=format&fit=crop&w=1000&q=80"
        );
        seedSpot(
                5,
                "青甘大环线星空自驾",
                "中国 · 西北",
                "¥1899 起",
                "4.9",
                "https://images.unsplash.com/photo-1501785888041-af3ef285b470?auto=format&fit=crop&w=1000&q=80"
        );
    }

    private void seedGuideCards() {
        if (guideCardRepository.count() > 0) {
            return;
        }
        seedGuide(
                0,
                "第一次带父母出境？这份省心清单请收藏",
                "https://images.unsplash.com/photo-1488085061387-422e29b40080?auto=format&fit=crop&w=1000&q=80",
                "21.4k"
        );
        seedGuide(
                1,
                "雨季也好玩：东南亚避坑与替代玩法",
                "https://images.unsplash.com/photo-1503220317375-aaad61436b1b?auto=format&fit=crop&w=1000&q=80",
                "16.8k"
        );
        seedGuide(
                2,
                "预算不变体验翻倍：机酒组合最优解",
                "https://images.unsplash.com/photo-1469474968028-56623f02e42e?auto=format&fit=crop&w=1000&q=80",
                "12.9k"
        );
    }

    private void seedEnterpriseCards() {
        if (enterpriseCardRepository.count() > 0) {
            return;
        }
        seedEnterprise(0, "商旅管理平台", "统一审批、预算控制、发票归集与出行合规，支持多部门权限管理。");
        seedEnterprise(1, "开放 API 接入", "提供行程推荐、目的地画像、供应商聚合能力，可快速接入现有系统。");
        seedEnterprise(2, "SaaS 定制方案", "按业务场景定制推荐规则、品牌样式与权限模型，支持私有化部署。");
    }

    private void seedAdminUser() {
        boolean hasAdmin = userAccountRepository.findAll().stream()
                .anyMatch(user -> "ADMIN".equalsIgnoreCase(user.getRole()));
        if (hasAdmin) {
            return;
        }
        UserAccount admin = new UserAccount();
        admin.setUsername("admin");
        admin.setEmail("admin@travelflow.local");
        admin.setPasswordHash(passwordEncoder.encode("admin123456"));
        admin.setRole("ADMIN");
        admin.setEnabled(true);
        userAccountRepository.save(admin);
    }

    private void seedCategory(int sort, String name, String keyword) {
        PortalCategoryEntity item = new PortalCategoryEntity();
        item.setSortOrder(sort);
        item.setEnabled(true);
        item.setName(name);
        item.setKeyword(keyword);
        categoryRepository.save(item);
    }

    private void seedSlide(int sort, String title, String subtitle, String description, String image) {
        PortalSlideEntity item = new PortalSlideEntity();
        item.setSortOrder(sort);
        item.setEnabled(true);
        item.setTitle(title);
        item.setSubtitle(subtitle);
        item.setDescription(description);
        item.setImage(image);
        slideRepository.save(item);
    }

    private void seedSpot(int sort, String title, String location, String price, String rating, String image) {
        PortalSpotCardEntity item = new PortalSpotCardEntity();
        item.setSortOrder(sort);
        item.setEnabled(true);
        item.setTitle(title);
        item.setLocation(location);
        item.setPrice(price);
        item.setRating(rating);
        item.setImage(image);
        spotCardRepository.save(item);
    }

    private void seedGuide(int sort, String title, String cover, String reads) {
        PortalGuideCardEntity item = new PortalGuideCardEntity();
        item.setSortOrder(sort);
        item.setEnabled(true);
        item.setTitle(title);
        item.setCover(cover);
        item.setReads(reads);
        guideCardRepository.save(item);
    }

    private void seedEnterprise(int sort, String title, String description) {
        PortalEnterpriseCardEntity item = new PortalEnterpriseCardEntity();
        item.setSortOrder(sort);
        item.setEnabled(true);
        item.setTitle(title);
        item.setDescription(description);
        enterpriseCardRepository.save(item);
    }
}
