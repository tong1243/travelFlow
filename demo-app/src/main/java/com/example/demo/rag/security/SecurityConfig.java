package com.example.demo.rag.security;

import com.example.demo.rag.filter.ApiAccessLogFilter;
import com.example.demo.rag.filter.ApiRateLimitFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.Instant;
import java.util.Map;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ApiRateLimitFilter apiRateLimitFilter;
    private final ApiAccessLogFilter apiAccessLogFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          ApiRateLimitFilter apiRateLimitFilter,
                          ApiAccessLogFilter apiAccessLogFilter,
                          ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.apiRateLimitFilter = apiRateLimitFilter;
        this.apiAccessLogFilter = apiAccessLogFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/travel/**").authenticated()
                        .requestMatchers("/api/portal/spot-plan/**").authenticated()
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().permitAll()
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) ->
                                writeError(response, 401, "未登录或登录已过期，请重新登录"))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeError(response, 403, "当前账号无权限访问该资源"))
                )
                .addFilterBefore(apiAccessLogFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(apiRateLimitFilter, ApiAccessLogFilter.class)
                .addFilterAfter(jwtAuthenticationFilter, ApiRateLimitFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private void writeError(HttpServletResponse response, int status, String message) {
        try {
            if (response.isCommitted()) {
                return;
            }
            response.setStatus(status);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            Map<String, Object> body = Map.of(
                    "message", message,
                    "timestamp", Instant.now().toString()
            );
            response.getWriter().write(objectMapper.writeValueAsString(body));
        } catch (Exception ignored) {
            // Ignore writing exceptions in auth handlers.
        }
    }
}
