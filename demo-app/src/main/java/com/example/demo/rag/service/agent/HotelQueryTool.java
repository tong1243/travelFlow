package com.example.demo.rag.service.agent;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@Order(17)
public class HotelQueryTool implements AgentTool {

    private final HotelLookupService hotelLookupService;

    public HotelQueryTool(HotelLookupService hotelLookupService) {
        this.hotelLookupService = hotelLookupService;
    }

    @Override
    public String toolName() {
        return "酒店查询";
    }

    @Override
    public boolean shouldRun(AgentToolExecutionContext context) {
        if (context == null || context.request() == null) {
            return true;
        }
        String preference = context.request().hotelPreference();
        if (preference != null && !preference.isBlank()) {
            String normalized = preference.trim().toLowerCase(Locale.ROOT);
            if (normalized.contains("已预定") || normalized.contains("已预订") || normalized.contains("booked")) {
                return false;
            }
        }
        Boolean hotelRecommendation = context.request().hotelRecommendation();
        return hotelRecommendation == null || hotelRecommendation;
    }

    @Override
    public void execute(AgentToolExecutionContext context) {
        AgentToolRuntime runtime = context.runtime();
        String priceRange = context.request() == null ? null : context.request().hotelPriceRange();
        HotelLookupService.HotelLookupResult result = hotelLookupService.lookupHotels(runtime.getQuestion(), priceRange);
        runtime.addTrace(toolName(), "mode=hotel_lookup; parse=city,checkin,checkout,preference,price_range", result.summary());
    }
}
