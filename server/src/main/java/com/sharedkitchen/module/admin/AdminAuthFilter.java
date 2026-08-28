package com.sharedkitchen.module.admin;

import com.sharedkitchen.common.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/** /api/admin/** 独立鉴权：要求 admin: 前缀的管理员 token（登录接口除外）。由 WebConfig 注册为 Bean。 */
public class AdminAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public AdminAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            reject(response);
            return;
        }
        try {
            String subject = jwtService.parseSubject(header.substring(7));
            if (subject == null || !subject.startsWith("admin:")) {
                reject(response, "无后台权限");
                return;
            }
            request.setAttribute("adminId", Long.valueOf(subject.substring(6)));
            chain.doFilter(request, response);
        } catch (Exception e) {
            reject(response, "登录已过期，请重新登录");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "/api/admin/login".equals(request.getRequestURI());
    }

    private void reject(HttpServletResponse response) throws IOException {
        reject(response, "请先登录后台");
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"code\":401,\"message\":\"" + message + "\",\"data\":null}");
    }
}
