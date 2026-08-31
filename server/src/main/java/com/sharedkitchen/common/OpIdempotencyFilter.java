package com.sharedkitchen.common;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * 设备直连同步的幂等保护：两台设备离线互同步后各自回写同一批操作(X-Op-Id)，
 * 对带 X-Op-Id 的写请求去重——首次执行并缓存成功响应，重放直接返回缓存，
 * 避免同一操作被写入两次。
 */
public class OpIdempotencyFilter implements Filter {

    private final SyncOpRepository repo;

    public OpIdempotencyFilter(SyncOpRepository repo) {
        this.repo = repo;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) req;
        HttpServletResponse httpRes = (HttpServletResponse) res;
        String opId = http.getHeader("X-Op-Id");
        String method = http.getMethod();
        boolean mutating = "POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method);
        if (opId == null || opId.isBlank() || !mutating || !http.getRequestURI().startsWith("/api/")) {
            chain.doFilter(req, res);
            return;
        }

        Optional<SyncOp> existing = repo.findByOpId(opId);
        if (existing.isPresent()) {
            httpRes.setStatus(existing.get().getStatus());
            httpRes.setContentType("application/json;charset=UTF-8");
            httpRes.getWriter().write(existing.get().getResponseBody());
            return;
        }

        // Spring 写 JSON 走 getOutputStream()，必须用 ContentCachingResponseWrapper 才拦得住
        ContentCachingResponseWrapper wrapped = new ContentCachingResponseWrapper(httpRes);

        chain.doFilter(req, wrapped);

        int status = wrapped.getStatus();
        byte[] raw = wrapped.getContentAsByteArray();
        String body = new String(raw, StandardCharsets.UTF_8);
        httpRes.setStatus(status);
        httpRes.setContentType("application/json;charset=UTF-8");
        httpRes.getOutputStream().write(raw);

        // 只缓存成功响应；失败(4xx/5xx)允许修正后重试
        if (status < 400) {
            SyncOp op = new SyncOp();
            op.setOpId(opId);
            Long kitchenId = null;
            if (http.getRequestURI().startsWith("/api/kitchens/")) {
                try {
                    kitchenId = Long.parseLong(http.getRequestURI().split("/")[3]);
                } catch (Exception ignore) { /* 路径不含厨房时为空 */ }
            }
            op.setKitchenId(kitchenId);
            op.setStatus(status);
            op.setResponseBody(body);
            op.setCreatedAt(Instant.now().toString());
            if (repo.findByOpId(opId).isEmpty()) {
                repo.save(op);
            }
        }
    }
}
