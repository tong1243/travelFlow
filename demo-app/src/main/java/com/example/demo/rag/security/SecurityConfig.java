package com.example.demo.rag.security;

import com.example.demo.rag.filter.ApiAccessLogFilter;
import com.example.demo.rag.filter.ApiRateLimitFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
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
/**
 * SecurityConfig类。
 * 该类型负责认证授权与访问控制，保障系统安全边界。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 */
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ApiRateLimitFilter apiRateLimitFilter;
    private final ApiAccessLogFilter apiAccessLogFilter;
    private final ObjectMapper objectMapper;

    /**
     * 构造并初始化 SecurityConfig 对象。
     * 该构造方法用于注入运行所需依赖，保证对象创建后即可参与完整流程。
     * 该方法用于认证授权处理，确保访问链路符合安全策略。
     * @param jwtAuthenticationFilter 输入参数 jwtAuthenticationFilter，用于参与本次处理流程。
     * @param apiRateLimitFilter 输入参数 apiRateLimitFilter，用于参与本次处理流程。
     * @param apiAccessLogFilter 输入参数 apiAccessLogFilter，用于参与本次处理流程。
     * @param objectMapper 输入参数 objectMapper，用于参与本次处理流程。
     */
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
    /**
     * 执行 securityFilterChain 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法用于认证授权处理，确保访问链路符合安全策略。
     * @param http 输入参数 http，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()
                        .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/v1/map/route-plan").permitAll()
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
    /**
     * 执行 passwordEncoder 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法用于认证授权处理，确保访问链路符合安全策略。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 执行 writeError 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法用于认证授权处理，确保访问链路符合安全策略。
     * @param response 输入参数 response，用于参与本次处理流程。
     * @param status 输入参数 status，用于参与本次处理流程。
     * @param message 输入参数 message，用于参与本次处理流程。
     */
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
            // 鉴权处理器写响应异常时忽略，避免再次抛错影响主流程。
        }
    }
}
