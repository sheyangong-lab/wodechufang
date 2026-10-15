package com.sharedkitchen.module.admin;

import com.sharedkitchen.common.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@Validated
public class AdminController {

    private final AdminService adminService;

    public record LoginReq(@NotBlank String username, @NotBlank String password) {}

    public record BanReq(boolean ban) {}

    // ----- 登录 / 概览 -----

    @PostMapping("/login")
    public ApiResponse<Map<String, String>> login(@RequestBody @Validated LoginReq req) {
        return ApiResponse.ok(Map.of("token", adminService.login(req.username(), req.password())));
    }

    @GetMapping("/overview")
    public ApiResponse<AdminService.Overview> overview() {
        return ApiResponse.ok(adminService.overview());
    }

    // ----- 厨房 -----

    @GetMapping("/kitchens")
    public ApiResponse<List<AdminService.KitchenAdminView>> kitchens(
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(adminService.kitchens(keyword));
    }

    @PostMapping("/kitchens/{id}/ban")
    public ApiResponse<AdminService.KitchenAdminView> setKitchenBan(
            @PathVariable Long id, @RequestBody BanReq req) {
        return ApiResponse.ok(adminService.setBan(id, req.ban()));
    }

    // ----- 用户 -----

    @GetMapping("/users")
    public ApiResponse<List<AdminService.UserAdminView>> users(
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(adminService.users(keyword));
    }

    @PostMapping("/users/{id}/ban")
    public ApiResponse<Void> setUserBan(@PathVariable Long id, @RequestBody BanReq req) {
        adminService.setUserBan(id, req.ban());
        return ApiResponse.ok();
    }

    // ----- 运行时设置（AI识别模型/Key、登录有效期、后台网段） -----

    private final AppSettingService settingService;

    public AdminController(AdminService adminService, AppSettingService settingService) {
        this.adminService = adminService;
        this.settingService = settingService;
    }

    @GetMapping("/settings")
    public ApiResponse<Map<String, Object>> settings() {
        return ApiResponse.ok(settingService.all());
    }

    public record SettingReq(@NotBlank String key, String value) {}

    @PostMapping("/settings")
    public ApiResponse<Map<String, Object>> saveSetting(@RequestBody @Validated SettingReq req) {
        settingService.save(req.key(), req.value());
        return ApiResponse.ok(settingService.all());
    }

}
