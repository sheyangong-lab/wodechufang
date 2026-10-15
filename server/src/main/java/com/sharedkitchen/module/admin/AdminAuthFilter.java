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
    private final AppSettingService settings;

    public AdminAuthFilter(JwtService jwtService, AppSettingService settings) {
        this.jwtService = jwtService;
        this.settings = settings;
    }

    /**
     * 后台只允许内网访问：客户端 IP 必须命中 admin.allowed-cidrs（后台设置页可改）。
     * 经反代时取 X-Forwarded-For 首个 IP。公网访问 /api/admin/** 与 /admin/ 一律 403。
     */
    private boolean fromAllowedCidr(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        ip = ip == null || ip.isBlank() ? request.getRemoteAddr() : ip.split(",")[0].trim();
        String cidrs = settings.get("admin.allowed-cidrs", "192.168.0.0/16,10.0.0.0/8,172.16.0.0/12,127.0.0.1");
        for (String cidr : cidrs.split(",")) {
            if (inCidr(ip, cidr.trim())) {
                return true;
            }
        }
        return "127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip);
    }

    private boolean inCidr(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/");
            byte[] cidrAddr = java.net.InetAddress.getByName(parts[0]).getAddress();
            int prefix = Integer.parseInt(parts[1]);
            byte[] addr = java.net.InetAddress.getByName(ip).getAddress();
            if (addr.length != cidrAddr.length) {
                return false;
            }
            int fullBytes = prefix / 8;
            int remBits = prefix % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (addr[i] != cidrAddr[i]) {
                    return false;
                }
            }
            if (remBits > 0 && fullBytes < addr.length) {
                int mask = 0xFF << (8 - remBits);
                if ((addr[fullBytes] & mask) != (cidrAddr[fullBytes] & mask)) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!fromAllowedCidr(request)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"后台仅限内网访问\",\"data\":null}");
            return;
        }
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
        } catch (Exception e) {
            reject(response, "登录已过期，请重新登录");
            return;
        }
        // 校验通过后再放行；下游异常交给全局异常处理器，不能在这里被吞成 401
        chain.doFilter(request, response);
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
