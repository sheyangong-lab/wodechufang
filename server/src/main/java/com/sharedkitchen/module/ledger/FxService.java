package com.sharedkitchen.module.ledger;

import com.sharedkitchen.module.admin.AppSettingService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 汇率服务：HKD/USD → CNY。
 * 来源优先级：后台设置(fx.hkdcny/fx.usdcny 手动值) > frankfurter.app 每日牌价(启动+每6h拉取) > 内置默认。
 * 拉取失败静默降级（境内网络可能访问不了），后台设置页可随时手动改。
 */
@Service
public class FxService {

    private static final Logger log = LoggerFactory.getLogger(FxService.class);
    private static final double DEFAULT_HKD = 0.92;
    private static final double DEFAULT_USD = 7.24;

    private final AppSettingService settings;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private volatile double hkd = DEFAULT_HKD;
    private volatile double usd = DEFAULT_USD;

    public FxService(AppSettingService settings) {
        this.settings = settings;
        refreshFromSettings();
    }

    public double rateOf(String currency) {
        return switch (currency.toUpperCase()) {
            case "HKD" -> hkd;
            case "USD" -> usd;
            default -> 1.0;
        };
    }

    public double hkd() {
        return hkd;
    }

    public double usd() {
        return usd;
    }

    /** 后台手动设置的值优先（存在即用）。 */
    private void refreshFromSettings() {
        try {
            String h = settings.get("fx.hkdcny", "");
            String u = settings.get("fx.usdcny", "");
            if (!h.isBlank()) {
                hkd = Double.parseDouble(h.trim());
            }
            if (!u.isBlank()) {
                usd = Double.parseDouble(u.trim());
            }
        } catch (Exception ignore) { /* 保持当前值 */ }
    }

    /** 每 6 小时尝试拉一次最新牌价（未手动锁定时才覆盖）。 */
    @Scheduled(initialDelay = 3_000, fixedDelay = 6 * 3600_000)
    public void refresh() {
        refreshFromSettings();
        if (!settings.get("fx.hkdcny", "").isBlank() || !settings.get("fx.usdcny", "").isBlank()) {
            return; // 手动锁定，不自动覆盖
        }
        try {
            hkd = fetch("HKD", hkd);
            usd = fetch("USD", usd);
            log.info("汇率已更新: HKD={}, USD={}", hkd, usd);
        } catch (Exception e) {
            log.info("汇率拉取失败，沿用当前值 HKD={}, USD={}", hkd, usd);
        }
    }

    private double fetch(String from, double fallback) throws Exception {
        HttpResponse<String> resp = http.send(
                HttpRequest.newBuilder(URI.create("https://api.frankfurter.app/latest?from=" + from + "&to=CNY"))
                        .timeout(Duration.ofSeconds(8)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            return fallback;
        }
        // {"amount":1.0,"base":"HKD","date":"2026-10-16","rates":{"CNY":0.92xx}}
        String body = resp.body();
        int i = body.indexOf("\"CNY\":");
        if (i < 0) {
            return fallback;
        }
        int end = body.indexOf('}', i);
        String num = body.substring(i + 6, end).replaceAll("[^0-9.]", "");
        return num.isEmpty() ? fallback : Double.parseDouble(num);
    }
}
