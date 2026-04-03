package com.example.demo.rag.controller;

import com.example.demo.rag.dto.TripPlanResponse;
import com.example.demo.rag.dto.TripPlanUpsertRequest;
import com.example.demo.rag.security.AuthenticatedUser;
import com.example.demo.rag.service.TripPlanService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/trips")
public class TripPlanController {

    private final TripPlanService tripPlanService;

    public TripPlanController(TripPlanService tripPlanService) {
        this.tripPlanService = tripPlanService;
    }

    @GetMapping
    public List<TripPlanResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return tripPlanService.listByUser(user.getId());
    }

    @GetMapping("/{id}")
    public TripPlanResponse detail(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable("id") Long id) {
        return tripPlanService.getById(user.getId(), id);
    }

    @PostMapping
    public TripPlanResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                                   @Valid @RequestBody TripPlanUpsertRequest request) {
        return tripPlanService.create(user.getId(), request);
    }

    @PutMapping("/{id}")
    public TripPlanResponse update(@AuthenticationPrincipal AuthenticatedUser user,
                                   @PathVariable("id") Long id,
                                   @Valid @RequestBody TripPlanUpsertRequest request) {
        return tripPlanService.update(user.getId(), id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable("id") Long id) {
        tripPlanService.delete(user.getId(), id);
    }
}
