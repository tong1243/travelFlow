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
/**
 * ApiRateLimitFilter类。
 * 该类型负责请求链路的前后置处理，统一处理审计与限流等横切逻辑。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 */
public class ApiRateLimitFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties rateLimitProperties;
    private final ObjectMapper objectMapper;

    /**
     * 构造并初始化 ApiRateLimitFilter 对象。
     * 该构造方法用于注入运行所需依赖，保证对象创建后即可参与完整流程。
     * 该方法运行在过滤链上，用于处理请求前置校验和响应后置动作。
     * @param redisTemplate 输入参数 redisTemplate，用于参与本次处理流程。
     * @param rateLimitProperties 输入参数 rateLimitProperties，用于参与本次处理流程。
     * @param objectMapper 输入参数 objectMapper，用于参与本次处理流程。
     */
    public ApiRateLimitFilter(StringRedisTemplate redisTemplate,
                              RateLimitProperties rateLimitProperties,
                              ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.rateLimitProperties = rateLimitProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    /**
     * 执行 shouldNotFilter 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法运行在过滤链上，用于处理请求前置校验和响应后置动作。
     * @param request 输入参数 request，用于参与本次处理流程。
     * @return 返回判断结果：`true` 表示条件成立，`false` 表示条件不成立。
     */
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/");
    }

    @Override
    /**
     * 执行 doFilterInternal 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法运行在过滤链上，用于处理请求前置校验和响应后置动作。
     * @param request 输入参数 request，用于参与本次处理流程。
     * @param response 输入参数 response，用于参与本次处理流程。
     * @param filterChain 输入参数 filterChain，用于参与本次处理流程。
     */
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
            body.put("message", "请求过于频繁，请稍后再试。");
            body.put("timestamp", Instant.now().toString());
            response.getWriter().write(objectMapper.writeValueAsString(body));
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 执行 resolveIdentifier 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法运行在过滤链上，用于处理请求前置校验和响应后置动作。
     * @param request 输入参数 request，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    private String resolveIdentifier(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return "u:" + user.getId();
        }
        return "ip:" + request.getRemoteAddr();
    }
}
