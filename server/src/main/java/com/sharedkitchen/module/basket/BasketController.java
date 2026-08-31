package com.sharedkitchen.module.basket;

import com.sharedkitchen.common.ApiResponse;
import com.sharedkitchen.common.JwtAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
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
@RequestMapping("/api/kitchens/{kitchenId}/basket")
@Validated
public class BasketController {

    private final BasketService basketService;

    public BasketController(BasketService basketService) {
        this.basketService = basketService;
    }

    public record AddReq(@NotBlank String name, String quantity) {}

    @GetMapping("/items")
    public ApiResponse<List<BasketService.BasketItemView>> list(
            HttpServletRequest request, @PathVariable Long kitchenId) {
        return ApiResponse.ok(basketService.list(userId(request), kitchenId).stream()
                .map(this::toView).collect(java.util.stream.Collectors.toList()));
    }

    @PostMapping("/items")
    public ApiResponse<BasketService.BasketItemView> add(
            HttpServletRequest request, @PathVariable Long kitchenId,
            @RequestBody AddReq req) {
        return ApiResponse.ok(toView(basketService.add(userId(request), kitchenId, req.name(), req.quantity())));
    }

    @PostMapping("/items/{itemId}/toggle")
    public ApiResponse<Void> toggle(
            HttpServletRequest request, @PathVariable Long kitchenId, @PathVariable Long itemId) {
        basketService.toggle(userId(request), kitchenId, itemId);
        return ApiResponse.ok();
    }

    @DeleteMapping("/items/{itemId}")
    public ApiResponse<Void> remove(
            HttpServletRequest request, @PathVariable Long kitchenId, @PathVariable Long itemId) {
        basketService.remove(userId(request), kitchenId, itemId);
        return ApiResponse.ok();
    }

    @PostMapping("/clear-checked")
    public ApiResponse<Integer> clearChecked(
            HttpServletRequest request, @PathVariable Long kitchenId) {
        return ApiResponse.ok(basketService.clearChecked(userId(request), kitchenId));
    }

    /** 从指定日期的下单用料生成菜篮（与冰箱比对，缺什么加什么）。 */
    @PostMapping("/generate")
    public ApiResponse<BasketService.GenerateResult> generate(
            HttpServletRequest request, @PathVariable Long kitchenId,
            @RequestParam(required = false) String date) {
        BasketService.GenerateResult r = basketService.generate(userId(request), kitchenId, date);
        return ApiResponse.ok(r);
    }

    private BasketService.BasketItemView toView(BasketItem i) {
        return new BasketService.BasketItemView(
                i.getId(), i.getName(), i.getQuantity(), i.getChecked(), i.getSource());
    }

    private Long userId(HttpServletRequest request) {
        return (Long) request.getAttribute(JwtAuthFilter.ATTR_USER_ID);
    }
}
