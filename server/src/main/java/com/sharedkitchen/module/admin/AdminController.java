package com.sharedkitchen.module.admin;

import com.sharedkitchen.common.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@Validated
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    public record LoginReq(@NotBlank String username, @NotBlank String password) {}

    public record GrantVipReq(int days) {}

    public record QuotaReq(Integer dishQuota, Integer categoryQuota) {}

    public record BanReq(boolean ban) {}

    public record GenCodesReq(@NotNull Long planId, int count, String batch) {}

    // ----- 登录 / 概览 -----

    @PostMapping("/login")
    public ApiResponse<Map<String, String>> login(@RequestBody @Validated LoginReq req) {
        return ApiResponse.ok(Map.of("token", adminService.login(req.username(), req.password())));
    }

    @GetMapping("/overview")
    public ApiResponse<AdminService.Overview> overview() {
        return ApiResponse.ok(adminService.overview());
    }

    // ----- 厨房 & VIP -----

    @GetMapping("/kitchens")
    public ApiResponse<List<AdminService.KitchenAdminView>> kitchens(
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(adminService.kitchens(keyword));
    }

    @PostMapping("/kitchens/{id}/vip")
    public ApiResponse<AdminService.KitchenAdminView> grantVip(
            @PathVariable Long id, @RequestBody @Validated GrantVipReq req) {
        return ApiResponse.ok(adminService.grantVip(id, req.days()));
    }

    @DeleteMapping("/kitchens/{id}/vip")
    public ApiResponse<AdminService.KitchenAdminView> revokeVip(@PathVariable Long id) {
        return ApiResponse.ok(adminService.revokeVip(id));
    }

    @PutMapping("/kitchens/{id}/quota")
    public ApiResponse<AdminService.KitchenAdminView> setQuota(
            @PathVariable Long id, @RequestBody QuotaReq req) {
        return ApiResponse.ok(adminService.setQuota(id, req.dishQuota(), req.categoryQuota()));
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

    @PostMapping("/users/{id}/points")
    public ApiResponse<Void> grantPoints(
            @PathVariable Long id, @RequestBody Map<String, Long> req) {
        adminService.grantPoints(id, req.getOrDefault("points", 0L));
        return ApiResponse.ok();
    }

    // ----- 兑换码 -----

    @GetMapping("/codes")
    public ApiResponse<List<AdminService.CodeAdminView>> codes(
            @RequestParam(required = false) String batch) {
        return ApiResponse.ok(adminService.codes(batch));
    }

    @PostMapping("/codes/generate")
    public ApiResponse<Map<String, String>> generate(
            @RequestBody @Validated GenCodesReq req) {
        String codes = adminService.generateCodes(req.planId(), req.count(), req.batch());
        return ApiResponse.ok(Map.of("codes", codes));
    }

    @DeleteMapping("/codes/{id}")
    public ApiResponse<Void> disableCode(@PathVariable Long id) {
        adminService.disableCode(id);
        return ApiResponse.ok();
    }
}
