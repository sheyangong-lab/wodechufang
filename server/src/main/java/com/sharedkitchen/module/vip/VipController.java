package com.sharedkitchen.module.vip;

import com.sharedkitchen.common.ApiResponse;
import com.sharedkitchen.common.JwtAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/kitchens/{kitchenId}/vip")
@Validated
public class VipController {

    private final VipService vipService;

    public VipController(VipService vipService) {
        this.vipService = vipService;
    }

    public record RedeemReq(@NotBlank String code) {}

    public record PurchaseReq(Long planId) {}

    @GetMapping("/status")
    public ApiResponse<VipService.VipStatus> status(
            HttpServletRequest request, @PathVariable Long kitchenId) {
        return ApiResponse.ok(vipService.status(kitchenId));
    }

    @PostMapping("/redeem")
    public ApiResponse<VipService.VipStatus> redeem(
            HttpServletRequest request, @PathVariable Long kitchenId,
            @RequestBody @Validated RedeemReq req) {
        Long userId = (Long) request.getAttribute(JwtAuthFilter.ATTR_USER_ID);
        return ApiResponse.ok(vipService.redeem(userId, kitchenId, req.code()));
    }

    /** 支付购买（预留）：接入微信支付/华为 IAP 前返回引导。 */
    @PostMapping("/purchase")
    public ApiResponse<Void> purchase(
            HttpServletRequest request, @PathVariable Long kitchenId,
            @RequestBody PurchaseReq req) {
        Long userId = (Long) request.getAttribute(JwtAuthFilter.ATTR_USER_ID);
        vipService.purchase(userId, kitchenId, req.planId());
        return ApiResponse.ok();
    }
}
