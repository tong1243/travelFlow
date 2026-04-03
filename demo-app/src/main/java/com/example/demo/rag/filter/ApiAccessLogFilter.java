package com.example.demo.rag.filter;

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
import java.util.UUID;

@Component
public class ApiAccessLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiAccessLogFilter.class);
    private static final String TRACE_ID = "traceId";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/");
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
            log.info("traceId={}, method={}, uri={}, status={}, costMs={}",
                    traceId,
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    cost
            );
            MDC.remove(TRACE_ID);
        }
    }
}
