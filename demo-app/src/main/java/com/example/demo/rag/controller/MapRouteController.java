package com.example.demo.rag.controller;

import com.example.demo.rag.dto.MapRoutePlanRequest;
import com.example.demo.rag.dto.MapRoutePlanResponse;
import com.example.demo.rag.service.MapRouteService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/map")
public class MapRouteController {

    private final MapRouteService mapRouteService;

    public MapRouteController(MapRouteService mapRouteService) {
        this.mapRouteService = mapRouteService;
    }

    @PostMapping("/route-plan")
    public MapRoutePlanResponse plan(@RequestBody MapRoutePlanRequest request) {
        if (request == null) {
            return new MapRoutePlanResponse("", "driving", "请求为空。", java.util.List.of());
        }
        return mapRouteService.planRoutes(request.city(), request.places(), request.travelMode());
    }
}

