package com.example.demo.rag.service;

import com.example.demo.rag.RagException;
import com.example.demo.rag.dto.AuthResponse;
import com.example.demo.rag.dto.LoginRequest;
import com.example.demo.rag.dto.RegisterRequest;
import com.example.demo.rag.dto.UserProfileResponse;
import com.example.demo.rag.entity.UserAccount;
import com.example.demo.rag.repo.UserAccountRepository;
import com.example.demo.rag.security.AuthenticatedUser;
import com.example.demo.rag.security.JwtTokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthService(UserAccountRepository userAccountRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenService jwtTokenService) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.username().trim();
        if (userAccountRepository.existsByUsername(username)) {
            throw new RagException("Username already exists.");
        }

        String email = normalizeEmail(request.email());
        if (email != null && userAccountRepository.existsByEmail(email)) {
            throw new RagException("Email already exists.");
        }

        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole("USER");
        user.setEnabled(true);
        user = userAccountRepository.save(user);

        return toAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        UserAccount user = userAccountRepository.findByUsername(request.username().trim())
                .orElseThrow(() -> new RagException("Invalid username or password."));
        if (!user.isEnabled()) {
            throw new RagException("User is disabled.");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new RagException("Invalid username or password.");
        }
        return toAuthResponse(user);
    }

    public UserProfileResponse me(Long userId) {
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new RagException("User not found."));
        return new UserProfileResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole());
    }

    private AuthResponse toAuthResponse(UserAccount user) {
        AuthenticatedUser principal = new AuthenticatedUser(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                user.getRole(),
                user.isEnabled()
        );
        String token = jwtTokenService.createToken(principal);
        return new AuthResponse(user.getId(), user.getUsername(), token, jwtTokenService.getExpireSeconds());
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String normalized = email.trim();
        return normalized.isBlank() ? null : normalized;
    }
}
