package com.example.demo.rag.filter;

import com.example.demo.rag.config.ApiAuditProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
/**
 * ApiAccessLogFilter类。
 * 该类型负责请求链路的前后置处理，统一处理审计与限流等横切逻辑。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 */
public class ApiAccessLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiAccessLogFilter.class);
    private static final String TRACE_ID = "traceId";
    private final ApiAuditProperties apiAuditProperties;

    /**
     * 构造并初始化 ApiAccessLogFilter 对象。
     * 该构造方法用于注入运行所需依赖，保证对象创建后即可参与完整流程。
     * 该方法运行在过滤链上，用于处理请求前置校验和响应后置动作。
     * @param apiAuditProperties 输入参数 apiAuditProperties，用于参与本次处理流程。
     */
    public ApiAccessLogFilter(ApiAuditProperties apiAuditProperties) {
        this.apiAuditProperties = apiAuditProperties;
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
        if (!apiAuditProperties.isEnabled()) {
            return true;
        }
        String uri = request.getRequestURI();
        if (apiAuditProperties.isIncludeAllApi()) {
            return !uri.startsWith("/api/");
        }
        return !uri.startsWith("/api/v1/");
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
        long start = System.currentTimeMillis();
        String traceId = request.getHeader("X-Trace-Id");
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        response.setHeader("X-Trace-Id", traceId);
        MDC.put(TRACE_ID, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            long cost = System.currentTimeMillis() - start;
            boolean candidateApi = isCandidateApi(request.getRequestURI(), apiAuditProperties.getCandidatePrefixes());
            log.info("traceId={}, method={}, uri={}, status={}, costMs={}, candidateApi={}",
                    traceId,
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    cost,
                    candidateApi
            );
            MDC.remove(TRACE_ID);
        }
    }

    /**
     * 执行 isCandidateApi 条件判断。
     * 该方法返回布尔判定结果，用于上层流程分支控制和策略选择。
     * 该方法运行在过滤链上，用于处理请求前置校验和响应后置动作。
     * @param uri 输入参数 uri，用于参与本次处理流程。
     * @param prefixes 输入参数 prefixes，用于参与本次处理流程。
     * @return 返回判断结果：`true` 表示条件成立，`false` 表示条件不成立。
     */
    private boolean isCandidateApi(String uri, List<String> prefixes) {
        if (uri == null || prefixes == null || prefixes.isEmpty()) {
            return false;
        }
        for (String prefix : prefixes) {
            if (prefix == null || prefix.isBlank()) {
                continue;
            }
            String normalized = prefix.trim();
            if (uri.equals(normalized) || uri.startsWith(normalized + "/")) {
                return true;
            }
        }
        return false;
    }
}
