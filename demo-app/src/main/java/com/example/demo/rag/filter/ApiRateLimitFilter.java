package com.example.demo.rag.filter;

import com.example.demo.rag.config.RateLimitProperties;
import com.example.demo.rag.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class ApiRateLimitFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties rateLimitProperties;
    private final ObjectMapper objectMapper;

    public ApiRateLimitFilter(StringRedisTemplate redisTemplate,
                              RateLimitProperties rateLimitProperties,
                              ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.rateLimitProperties = rateLimitProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        int limit = Math.max(30, rateLimitProperties.getPerMinute());
        String minuteBucket = String.valueOf(Instant.now().getEpochSecond() / 60);
        String identifier = resolveIdentifier(request);
        String key = "rate:api:v1:" + identifier + ":" + minuteBucket;

        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, 90, TimeUnit.SECONDS);
        }

        if (count != null && count > limit) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            Map<String, Object> body = new HashMap<>();
            body.put("message", "Rate limit exceeded. Please retry later.");
            body.put("timestamp", Instant.now().toString());
            response.getWriter().write(objectMapper.writeValueAsString(body));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String resolveIdentifier(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return "u:" + user.getId();
        }
        return "ip:" + request.getRemoteAddr();
    }
}
