package com.example.demo.assistant;

import com.example.demo.assistant.dto.AssistantResult;
import com.example.demo.assistant.dto.TravelBudgetRequest;
import com.example.demo.assistant.dto.TravelFileQaRequest;
import com.example.demo.assistant.dto.TravelFollowUpRequest;
import com.example.demo.assistant.dto.TravelPlanByFilesRequest;
import com.example.demo.assistant.dto.TravelPlanRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/travel")
public class TravelController {

    private final TravelAssistantService travelAssistantService;

    public TravelController(TravelAssistantService travelAssistantService) {
        this.travelAssistantService = travelAssistantService;
    }

    @PostMapping("/plan")
    @Deprecated(since = "2026-04", forRemoval = false)
    public AssistantResult generatePlan(@Valid @RequestBody TravelPlanRequest request) {
        return travelAssistantService.generateTravelPlan(request);
    }

    @PostMapping(value = "/plan/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generatePlanStream(@Valid @RequestBody TravelPlanRequest request) {
        SseEmitter emitter = new SseEmitter(0L);
        CompletableFuture.runAsync(() -> {
            try {
                travelAssistantService.streamTravelPlan(request, chunk -> sendEvent(emitter, "delta", chunk));
                sendEvent(emitter, "done", "[DONE]");
                emitter.complete();
            } catch (Exception ex) {
                sendEvent(emitter, "error", ex.getMessage() == null ? "Stream failed." : ex.getMessage());
                emitter.completeWithError(ex);
            }
        });
        return emitter;
    }

    @PostMapping(value = "/follow-up/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter followUpStream(@Valid @RequestBody TravelFollowUpRequest request) {
        SseEmitter emitter = new SseEmitter(0L);
        CompletableFuture.runAsync(() -> {
            try {
                travelAssistantService.streamFollowUp(request, chunk -> sendEvent(emitter, "delta", chunk));
                sendEvent(emitter, "done", "[DONE]");
                emitter.complete();
            } catch (Exception ex) {
                sendEvent(emitter, "error", ex.getMessage() == null ? "Stream failed." : ex.getMessage());
                emitter.completeWithError(ex);
            }
        });
        return emitter;
    }

    @PostMapping("/budget")
    @Deprecated(since = "2026-04", forRemoval = false)
    public AssistantResult estimateBudget(@Valid @RequestBody TravelBudgetRequest request) {
        return travelAssistantService.estimateBudget(request);
    }

    @PostMapping("/plan/files")
    @Deprecated(since = "2026-04", forRemoval = false)
    public AssistantResult planByFiles(@Valid @RequestBody TravelPlanByFilesRequest request) {
        return travelAssistantService.planByFiles(request);
    }

    @PostMapping("/file-qa")
    @Deprecated(since = "2026-04", forRemoval = false)
    public AssistantResult fileQa(@Valid @RequestBody TravelFileQaRequest request) {
        return travelAssistantService.askByFile(request.fileId(), request.question());
    }

    private static void sendEvent(SseEmitter emitter, String eventName, String data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data == null ? "" : data));
        } catch (Exception ex) {
            throw new AssistantException("Failed to push stream event.", ex);
        }
    }
}
