package com.example.demo.assistant.dto;

import java.util.List;

public record PortalHomeResponse(
        List<String> navItems,
        List<String> categories,
        List<String> suggestionPool,
        List<PortalSlideItem> slides,
        List<PortalSpotItem> spots,
        List<PortalGuideItem> guides,
        List<PortalEnterpriseItem> enterpriseCards
) {
}
