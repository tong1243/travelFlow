package com.example.demo.rag.service.agent;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(35)
public class WeatherQueryTool implements AgentTool {

    private final WeatherLookupService weatherLookupService;

    public WeatherQueryTool(WeatherLookupService weatherLookupService) {
        this.weatherLookupService = weatherLookupService;
    }

    @Override
    public String toolName() {
        return "天气查询";
    }

    @Override
    public boolean shouldRun(AgentToolExecutionContext context) {
        if (context == null || context.request() == null) {
            return true;
        }
        Boolean weatherQuery = context.request().weatherQuery();
        return weatherQuery == null || weatherQuery;
    }

    @Override
    public void execute(AgentToolExecutionContext context) {
        AgentToolRuntime runtime = context.runtime();
        WeatherLookupService.WeatherLookupResult result = weatherLookupService.lookupTravelWeather(runtime.getQuestion());
        runtime.setWeatherSummary(result.summary());
        runtime.addTrace(toolName(), "mode=weather_lookup; parse=city,date_range", result.summary());
    }
}
