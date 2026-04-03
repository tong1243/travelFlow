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
public class ApiAccessLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiAccessLogFilter.class);
    private static final String TRACE_ID = "traceId";
    private final ApiAuditProperties apiAuditProperties;

    public ApiAccessLogFilter(ApiAuditProperties apiAuditProperties) {
        this.apiAuditProperties = apiAuditProperties;
    }

    @Override
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
