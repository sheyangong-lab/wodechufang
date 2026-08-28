package com.sharedkitchen.module.health;

import com.sharedkitchen.common.ApiResponse;
import java.time.Instant;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 探活 + 数据库连通性：SELECT 1 失败时整体 500，由全局异常兜底。 */
    @GetMapping
    public ApiResponse<Map<String, Object>> health() {
        Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        return ApiResponse.ok(Map.of(
                "status", "UP",
                "db", one != null && one == 1 ? "UP" : "DOWN",
                "time", Instant.now().toString()
        ));
    }
}
