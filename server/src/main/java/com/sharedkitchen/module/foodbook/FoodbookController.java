package com.sharedkitchen.module.foodbook;

import com.sharedkitchen.common.ApiResponse;
import com.sharedkitchen.common.JwtAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
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
@RequestMapping("/api/kitchens/{kitchenId}/foodbook")
@Validated
public class FoodbookController {

    private final FoodbookService service;

    public FoodbookController(FoodbookService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<FoodbookService.ItemView>> page(
            HttpServletRequest request, @PathVariable Long kitchenId,
            @RequestParam String date) {
        return ApiResponse.ok(service.page(userId(request), kitchenId, date).stream()
                .map(this::toView).toList());
    }

    public record AddBatchReq(List<FoodbookService.AddReq> items) {}

    @PostMapping("/items")
    public ApiResponse<List<FoodbookService.ItemView>> addAll(
            HttpServletRequest request, @PathVariable Long kitchenId,
            @RequestParam String date, @RequestBody AddBatchReq req) {
        List<FoodbookItem> items = req.items().stream().map(a -> {
            FoodbookItem i = new FoodbookItem();
            i.setImageUrl(a.imageUrl());
            i.setX(a.x());
            i.setY(a.y());
            i.setWidth(a.width());
            i.setZIndex(a.zIndex());
            return i;
        }).toList();
        return ApiResponse.ok(service.addAll(userId(request), kitchenId, date, items).stream()
                .map(this::toView).toList());
    }

    public record PositionReq(Double x, Double y) {}

    @PutMapping("/items/{itemId}/position")
    public ApiResponse<Void> updatePosition(
            HttpServletRequest request, @PathVariable Long kitchenId, @PathVariable Long itemId,
            @RequestBody PositionReq req) {
        service.updatePosition(userId(request), kitchenId, itemId, req.x(), req.y());
        return ApiResponse.ok();
    }

    @DeleteMapping("/items/{itemId}")
    public ApiResponse<Void> remove(
            HttpServletRequest request, @PathVariable Long kitchenId, @PathVariable Long itemId) {
        service.remove(userId(request), kitchenId, itemId);
        return ApiResponse.ok();
    }

    private FoodbookService.ItemView toView(FoodbookItem i) {
        return new FoodbookService.ItemView(
                i.getId(), i.getImageUrl(), i.getX(), i.getY(), i.getWidth(), i.getZIndex());
    }

    private Long userId(HttpServletRequest request) {
        return (Long) request.getAttribute(JwtAuthFilter.ATTR_USER_ID);
    }
}
