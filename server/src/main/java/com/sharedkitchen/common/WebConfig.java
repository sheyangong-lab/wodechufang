package com.sharedkitchen.common;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final com.sharedkitchen.common.JwtService jwtService;

    public WebConfig(com.sharedkitchen.common.JwtService jwtService) {
        this.jwtService = jwtService;
    }

    /** 上传图片与导出文件的静态映射：/files/** → data/uploads/，/files/exports/** → data/exports/。 */
    @Override
    public void addResourceHandlers(org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/files/exports/**")
                .addResourceLocations("file:./data/exports/");
        registry.addResourceHandler("/files/**")
                .addResourceLocations("file:./data/uploads/");
    }

    /**
     * CORS 过滤器必须先于 JwtAuthFilter（order 0 < 10）：
     * 一是预检 OPTIONS 到不了 JwtAuthFilter 就被放行，二是 401 等过滤直接写出的响应也带跨点头。
     */
    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOriginPattern("*");
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        // /files/** 上传图也放开跨域：H5 端 canvas 读取像素（如抠图回显校验）需要
        source.registerCorsConfiguration("/files/**", config);
        FilterRegistrationBean<CorsFilter> registration =
                new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setOrder(0);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtFilter(JwtAuthFilter filter) {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/api/*");
        registration.setOrder(10);
        return registration;
    }

    /** 后台鉴权：先于 JwtAuthFilter，管理 /api/admin/**（登录除外）。 */
    @Bean
    public FilterRegistrationBean<com.sharedkitchen.module.admin.AdminAuthFilter> adminAuthFilter() {
        FilterRegistrationBean<com.sharedkitchen.module.admin.AdminAuthFilter> registration =
                new FilterRegistrationBean<>(new com.sharedkitchen.module.admin.AdminAuthFilter(jwtService));
        registration.addUrlPatterns("/api/admin/*");
        registration.setOrder(5);
        return registration;
    }
}
