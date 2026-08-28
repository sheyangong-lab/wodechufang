package com.sharedkitchen.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bearer JWT 认证过滤器：白名单直接放行；其余 /api/** 必须携带有效 token，
 * 校验通过后把 userId 放入 request attribute 供控制器读取。
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String ATTR_USER_ID = "userId";

    private static final List<String> WHITELIST = List.of(
            "/api/health",
            "/api/auth/sms-code",
            "/api/auth/register",
            "/api/auth/login"
    );

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            reject(response, "请先登录");
            return;
        }
        try {
            Long userId = jwtService.parseUserId(header.substring(7));
            request.setAttribute(ATTR_USER_ID, userId);
            chain.doFilter(request, response);
        } catch (Exception e) {
            reject(response, "登录已过期，请重新登录");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return WHITELIST.contains(request.getRequestURI());
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"code\":401,\"message\":\"" + message + "\",\"data\":null}");
    }
}
