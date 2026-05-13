package com.example.demo.rag.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.jsonwebtoken.JwtException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
/**
 * JwtAuthenticationFilter类。
 * 该类型负责认证授权与访问控制，保障系统安全边界。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final AppUserDetailsService userDetailsService;

    /**
     * 构造并初始化 JwtAuthenticationFilter 对象。
     * 该构造方法用于注入运行所需依赖，保证对象创建后即可参与完整流程。
     * 该方法用于认证授权处理，确保访问链路符合安全策略。
     * @param jwtTokenService 输入参数 jwtTokenService，用于参与本次处理流程。
     * @param userDetailsService 输入参数 userDetailsService，用于参与本次处理流程。
     */
    public JwtAuthenticationFilter(JwtTokenService jwtTokenService, AppUserDetailsService userDetailsService) {
        this.jwtTokenService = jwtTokenService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    /**
     * 执行 shouldNotFilter 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法用于认证授权处理，确保访问链路符合安全策略。
     * @param request 输入参数 request，用于参与本次处理流程。
     * @return 返回判断结果：`true` 表示条件成立，`false` 表示条件不成立。
     */
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    /**
     * 执行 doFilterInternal 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法用于认证授权处理，确保访问链路符合安全策略。
     * @param request 输入参数 request，用于参与本次处理流程。
     * @param response 输入参数 response，用于参与本次处理流程。
     * @param filterChain 输入参数 filterChain，用于参与本次处理流程。
     */
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = authorizationHeader.substring(7).trim();
        if (jwt.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                String username = jwtTokenService.extractUsername(jwt);
                AuthenticatedUser user = (AuthenticatedUser) userDetailsService.loadUserByUsername(username);
                if (jwtTokenService.isTokenValid(jwt, user)) {
                    UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                            user,
                            null,
                            user.getAuthorities()
                    );
                    token.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(token);
                }
            }
        } catch (JwtException | IllegalArgumentException ignored) {
            // 令牌无效时继续放行，由鉴权框架在受保护接口返回 401。
        } catch (RuntimeException ignored) {
            // 令牌无效时继续放行，由鉴权框架在受保护接口返回 401。
        }
        filterChain.doFilter(request, response);
    }
}
