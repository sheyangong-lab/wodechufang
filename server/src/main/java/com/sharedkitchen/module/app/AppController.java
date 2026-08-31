package com.sharedkitchen.module.app;

import com.sharedkitchen.common.ApiResponse;
import com.sharedkitchen.common.BusinessException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 应用自更新：版本信息来自 ./data/apk/version.json（发布脚本 scripts/publish-apk.sh 生成）。
 * 无版本文件时返回 hasUpdate=false 的占位，客户端提示暂无更新。
 */
@RestController
@RequestMapping("/api/app")
public class AppController {

    private static final Path VERSION_FILE = Path.of("./data/apk/version.json");

    @GetMapping("/version")
    public ApiResponse<Map<String, Object>> version(
            @RequestParam(required = false) String current) {
        if (!Files.exists(VERSION_FILE)) {
            return ApiResponse.ok(Map.of("hasUpdate", false, "message", "暂无可用更新"));
        }
        try {
            String json = Files.readString(VERSION_FILE);
            @SuppressWarnings("unchecked")
            java.util.HashMap<String, Object> meta =
                    new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, java.util.HashMap.class);
            meta.put("hasUpdate", true);
            Object fileName = meta.get("fileName");
            if (fileName != null) {
                meta.put("url", "/files/apk/" + fileName);
            }
            return ApiResponse.ok(meta);
        } catch (Exception e) {
            throw new BusinessException("版本信息读取失败");
        }
    }
}
