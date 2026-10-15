package com.sharedkitchen.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtService {

    private final SecretKey key;

    private final long defaultTtlHours;
    private final Object settingsProvider;

    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.ttl-hours:72}") long ttlHours,
                      org.springframework.context.ApplicationContext ctx) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.defaultTtlHours = ttlHours;
        // 延迟取 AppSettingService（避免模块间构造循环依赖）
        this.settingsProvider = ctx.getBeanProvider(com.sharedkitchen.module.admin.AppSettingService.class);
    }

    @SuppressWarnings("unchecked")
    private long ttlHours() {
        try {
            org.springframework.beans.factory.ObjectProvider<com.sharedkitchen.module.admin.AppSettingService> p =
                    (org.springframework.beans.factory.ObjectProvider<com.sharedkitchen.module.admin.AppSettingService>) settingsProvider;
            com.sharedkitchen.module.admin.AppSettingService s = p.getIfAvailable();
            return s == null ? defaultTtlHours : s.getLong("jwt.ttl-hours", defaultTtlHours);
        } catch (Exception e) {
            return defaultTtlHours;
        }
    }

    public String issue(Long userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttlHours(), ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }

    /** 后台管理员 token：subject 带 admin: 前缀。 */
    public String issueAdmin(Long adminId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("admin:" + adminId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttlHours(), ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }

    /** 解析并校验签名与有效期；失败抛 io.jsonwebtoken.JwtException。 */
    public Long parseUserId(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
        return Long.valueOf(claims.getSubject());
    }

    /** 解析 subject 原文（用户为数字串，管理员为 admin:{id}）；失败抛 JwtException。 */
    public String parseSubject(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload().getSubject();
    }
}
