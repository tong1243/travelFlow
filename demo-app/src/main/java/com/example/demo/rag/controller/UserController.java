package com.example.demo.rag.controller;

import com.example.demo.rag.dto.UserProfileResponse;
import com.example.demo.rag.security.AuthenticatedUser;
import com.example.demo.rag.service.AuthService;
import com.example.demo.rag.service.UserManagementService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final AuthService authService;
    private final UserManagementService userManagementService;

    public UserController(AuthService authService, UserManagementService userManagementService) {
        this.authService = authService;
        this.userManagementService = userManagementService;
    }

    @GetMapping("/me")
    @Deprecated(since = "2026-04", forRemoval = false)
    public UserProfileResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return authService.me(user.getId());
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Deprecated(since = "2026-04", forRemoval = false)
    public List<UserProfileResponse> listUsers() {
        return userManagementService.listUsers();
    }
}
