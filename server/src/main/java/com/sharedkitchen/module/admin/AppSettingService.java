package com.sharedkitchen.module.admin;

import com.sharedkitchen.common.BusinessException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 运行时配置：app_settings 表的 key-value，覆盖 application.yml 默认值。
 * GLM 识别模型/Key、JWT 有效期、后台允许网段等都走这里，后台保存后热生效。
 * api-key 读取接口自动脱敏（只回尾 4 位）。
 */
@Service
public class AppSettingService {

    /** 允许后台读写的配置键及说明（白名单，防任意键注入） */
    public static final Map<String, String> DEFINED_KEYS = Map.of(
            "glm.model", "AI识别模型",
            "glm.api-key", "GLM API Key",
            "jwt.ttl-hours", "登录有效期(小时)",
            "admin.allowed-cidrs", "后台允许网段(CIDR,逗号分隔)"
    );

    private final AppSettingRepository repo;

    public AppSettingService(AppSettingRepository repo) {
        this.repo = repo;
    }

    /** 读配置：DB 无值时回退默认（与 application.yml 保持一致）。 */
    public String get(String key, String fallback) {
        return repo.findByKey(key).map(AppSetting::getValue)
                .filter(v -> !v.isBlank()).orElse(fallback);
    }

    public long getLong(String key, long fallback) {
        try {
            return Long.parseLong(get(key, String.valueOf(fallback)).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** 全量读取（后台展示）；api-key 脱敏。 */
    public Map<String, Object> all() {
        Map<String, Object> out = new LinkedHashMap<>();
        DEFINED_KEYS.forEach((key, label) -> {
            String v = get(key, "");
            if ("glm.api-key".equals(key) && v.length() > 8) {
                v = "****" + v.substring(v.length() - 4);
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("value", v);
            item.put("label", label);
            out.put(key, item);
        });
        return out;
    }

    @Transactional
    public void save(String key, String value) {
        if (!DEFINED_KEYS.containsKey(key)) {
            throw new BusinessException("不支持的配置项: " + key);
        }
        if (value == null) {
            value = "";
        }
        // 脱敏值不覆盖真实 Key（保存 **** 尾4 形式时跳过）
        if ("glm.api-key".equals(key) && value.startsWith("****")) {
            return;
        }
        if ("jwt.ttl-hours".equals(key)) {
            try {
                long h = Long.parseLong(value.trim());
                if (h < 1 || h > 24 * 30) {
                    throw new BusinessException("有效期需在 1~720 小时之间");
                }
            } catch (NumberFormatException e) {
                throw new BusinessException("有效期必须是数字（小时）");
            }
        }
        AppSetting s = repo.findByKey(key).orElseGet(() -> {
            AppSetting n = new AppSetting();
            n.setKey(key);
            return n;
        });
        s.setValue(value.trim());
        s.setUpdatedAt(Instant.now().toString());
        repo.save(s);
    }
}
