package com.example.demo.rag.controller;

import com.example.demo.rag.dto.AuthResponse;
import com.example.demo.rag.dto.LoginRequest;
import com.example.demo.rag.dto.RegisterRequest;
import com.example.demo.rag.dto.UserProfileResponse;
import com.example.demo.rag.security.AuthenticatedUser;
import com.example.demo.rag.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public UserProfileResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return authService.me(user.getId());
    }
}
