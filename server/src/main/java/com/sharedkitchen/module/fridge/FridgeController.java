package com.sharedkitchen.module.fridge;

import com.sharedkitchen.common.ApiResponse;
import com.sharedkitchen.common.JwtAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/kitchens/{kitchenId}/fridge")
@Validated
public class FridgeController {

    private final FridgeService fridgeService;

    public FridgeController(FridgeService fridgeService) {
        this.fridgeService = fridgeService;
    }

    public record CategoryReq(@NotBlank String name) {}

    public record ItemsReq(@NotNull List<FridgeService.ItemReq> items) {}

    public record ClearReq(List<Long> ids) {}

    public record MatchReq(Long itemId, String ingredient) {}

    // ----- 类别 -----

    @GetMapping("/categories")
    public ApiResponse<List<FridgeService.FridgeCategoryView>> categories(
            HttpServletRequest request, @PathVariable Long kitchenId) {
        return ApiResponse.ok(fridgeService.listCategories(userId(request), kitchenId));
    }

    @PostMapping("/categories")
    public ApiResponse<FridgeService.FridgeCategoryView> createCategory(
            HttpServletRequest request, @PathVariable Long kitchenId,
            @RequestBody @Validated CategoryReq req) {
        return ApiResponse.ok(fridgeService.createCategory(userId(request), kitchenId, req.name()));
    }

    @DeleteMapping("/categories/{categoryId}")
    public ApiResponse<Void> deleteCategory(
            HttpServletRequest request, @PathVariable Long kitchenId, @PathVariable Long categoryId) {
        fridgeService.deleteCategory(userId(request), kitchenId, categoryId);
        return ApiResponse.ok();
    }

    // ----- 食材 -----

    @PostMapping("/items")
    public ApiResponse<List<FridgeService.FridgeItemView>> createItems(
            HttpServletRequest request, @PathVariable Long kitchenId,
            @RequestBody @Validated ItemsReq req) {
        return ApiResponse.ok(fridgeService.createItems(userId(request), kitchenId, req.items()));
    }

    @GetMapping("/items")
    public ApiResponse<List<FridgeService.FridgeItemView>> list(
            HttpServletRequest request, @PathVariable Long kitchenId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "all") String state) {
        return ApiResponse.ok(fridgeService.listItems(userId(request), kitchenId,
                categoryId, keyword, state));
    }

    @GetMapping("/summary")
    public ApiResponse<FridgeService.FridgeStateSummary> summary(
            HttpServletRequest request, @PathVariable Long kitchenId) {
        return ApiResponse.ok(fridgeService.stateSummary(userId(request), kitchenId));
    }

    @PostMapping("/clear")
    public ApiResponse<Integer> clear(
            HttpServletRequest request, @PathVariable Long kitchenId,
            @RequestBody ClearReq req) {
        return ApiResponse.ok(fridgeService.clearItems(userId(request), kitchenId, req.ids()));
    }

    @PostMapping("/match")
    public ApiResponse<List<FridgeService.MatchedDish>> match(
            HttpServletRequest request, @PathVariable Long kitchenId,
            @RequestBody @Validated MatchReq req) {
        return ApiResponse.ok(fridgeService.matchRecipes(userId(request), kitchenId,
                req.itemId(), req.ingredient()));
    }

    // ----- 临期通知 -----

    @PostMapping("/check-expiry")
    public ApiResponse<Integer> checkExpiry(
            HttpServletRequest request, @PathVariable Long kitchenId) {
        requireMember(request, kitchenId);
        return ApiResponse.ok(fridgeService.checkExpiry(kitchenId));
    }

    @GetMapping("/notifications")
    public ApiResponse<List<FridgeService.NotificationView>> notifications(
            HttpServletRequest request, @PathVariable Long kitchenId) {
        return ApiResponse.ok(fridgeService.listNotifications(userId(request), kitchenId));
    }

    @GetMapping("/notifications/unread-count")
    public ApiResponse<Long> unreadCount(HttpServletRequest request, @PathVariable Long kitchenId) {
        return ApiResponse.ok(fridgeService.unreadCount(userId(request), kitchenId));
    }

    @PostMapping("/notifications/{id}/read")
    public ApiResponse<Void> markRead(
            HttpServletRequest request, @PathVariable Long kitchenId, @PathVariable Long id) {
        fridgeService.markRead(userId(request), kitchenId, id);
        return ApiResponse.ok();
    }

    private Long userId(HttpServletRequest request) {
        return (Long) request.getAttribute(JwtAuthFilter.ATTR_USER_ID);
    }

    private void requireMember(HttpServletRequest request, Long kitchenId) {
        // 探活性调用：unread 等接口内部已校验；此处仅确保有身份
        if (request.getAttribute(JwtAuthFilter.ATTR_USER_ID) == null) {
            throw new com.sharedkitchen.common.BusinessException(401, "请先登录");
        }
    }
}
