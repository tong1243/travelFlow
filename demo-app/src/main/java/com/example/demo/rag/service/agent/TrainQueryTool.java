package com.example.demo.rag.service.agent;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(16)
public class TrainQueryTool implements AgentTool {

    private final TrainLookupService trainLookupService;

    public TrainQueryTool(TrainLookupService trainLookupService) {
        this.trainLookupService = trainLookupService;
    }

    @Override
    public String toolName() {
        return "车票查询";
    }

    @Override
    public boolean shouldRun(AgentToolExecutionContext context) {
        if (context == null || context.request() == null) {
            return true;
        }
        String travelMode = context.request().travelMode();
        return travelMode == null || travelMode.isBlank() || "公共交通".equals(travelMode.trim());
    }

    @Override
    public void execute(AgentToolExecutionContext context) {
        AgentToolRuntime runtime = context.runtime();
        TrainLookupService.TrainLookupResult result = trainLookupService.lookupTrains(runtime.getQuestion());
        runtime.addTrace(toolName(), "mode=train_lookup; parse=origin,destination,date", result.summary());
    }
}
