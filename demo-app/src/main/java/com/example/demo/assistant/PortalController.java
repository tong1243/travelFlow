package com.example.demo.assistant;

import com.example.demo.assistant.dto.PortalCategoryQueryResponse;
import com.example.demo.assistant.dto.PortalGuideItem;
import com.example.demo.assistant.dto.PortalHomeResponse;
import com.example.demo.assistant.dto.PortalSpotPlanRequest;
import com.example.demo.assistant.dto.PortalSpotItem;
import com.example.demo.assistant.dto.AssistantResult;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/portal")
public class PortalController {

    private final PortalContentService portalContentService;
    private final TravelAssistantService travelAssistantService;

    public PortalController(PortalContentService portalContentService, TravelAssistantService travelAssistantService) {
        this.portalContentService = portalContentService;
        this.travelAssistantService = travelAssistantService;
    }

    @GetMapping("/home")
    public PortalHomeResponse home() {
        return portalContentService.home();
    }

    @GetMapping("/suggest")
    public List<String> suggest(@RequestParam(value = "keyword", required = false) String keyword) {
        return portalContentService.suggest(keyword);
    }

    @GetMapping("/categories")
    @Deprecated(since = "2026-04", forRemoval = false)
    public List<String> categories() {
        return portalContentService.categories();
    }

    @GetMapping("/category-query")
    public PortalCategoryQueryResponse categoryQuery(@RequestParam("category") String category) {
        return portalContentService.categoryQuery(category);
    }

    @GetMapping("/spots")
    public List<PortalSpotItem> spots() {
        return portalContentService.spots();
    }

    @GetMapping("/guides")
    public List<PortalGuideItem> guides() {
        return portalContentService.guides();
    }

    @PostMapping("/spot-plan")
    @Deprecated(since = "2026-04", forRemoval = false)
    public AssistantResult spotPlan(@Valid @RequestBody PortalSpotPlanRequest request) {
        return travelAssistantService.generateSpotPlan(request);
    }

    @PostMapping(value = "/spot-plan/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter spotPlanStream(@Valid @RequestBody PortalSpotPlanRequest request) {
        SseEmitter emitter = new SseEmitter(0L);
        CompletableFuture.runAsync(() -> {
            try {
                travelAssistantService.streamSpotPlan(request, chunk -> sendEvent(emitter, "delta", chunk));
                sendEvent(emitter, "done", "[DONE]");
                emitter.complete();
            } catch (Exception ex) {
                sendEvent(emitter, "error", ex.getMessage() == null ? "流式输出失败。" : ex.getMessage());
                emitter.complete();
            }
        });
        return emitter;
    }

    private static void sendEvent(SseEmitter emitter, String eventName, String data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data == null ? "" : data));
        } catch (Exception ignored) {
            // 客户端主动断开或网络抖动时，忽略发送异常，避免触发二次异常处理。
        }
    }
}
