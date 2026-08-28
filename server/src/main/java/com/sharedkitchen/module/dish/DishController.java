package com.sharedkitchen.module.dish;

import com.sharedkitchen.common.ApiResponse;
import com.sharedkitchen.common.JwtAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Validated
public class DishController {

    private final DishService dishService;

    public DishController(DishService dishService) {
        this.dishService = dishService;
    }

    public record CategoryReq(@NotBlank String name) {}

    public record StatusReq(int status) {}

    // ----- 分类 -----

    @GetMapping("/kitchens/{kitchenId}/categories")
    public ApiResponse<List<DishService.CategoryView>> categories(
            HttpServletRequest request, @PathVariable Long kitchenId) {
        Long userId = userId(request);
        return ApiResponse.ok(dishService.listCategories(userId, kitchenId));
    }

    @PostMapping("/kitchens/{kitchenId}/categories")
    public ApiResponse<DishService.CategoryView> createCategory(
            HttpServletRequest request, @PathVariable Long kitchenId,
            @RequestBody @Validated CategoryReq req) {
        return ApiResponse.ok(dishService.createCategory(userId(request), kitchenId, req.name()));
    }

    @DeleteMapping("/kitchens/{kitchenId}/categories/{categoryId}")
    public ApiResponse<Void> deleteCategory(
            HttpServletRequest request, @PathVariable Long kitchenId, @PathVariable Long categoryId) {
        dishService.deleteCategory(userId(request), kitchenId, categoryId);
        return ApiResponse.ok();
    }

    // ----- 菜谱 -----

    @PostMapping("/kitchens/{kitchenId}/dishes")
    public ApiResponse<DishView> create(
            HttpServletRequest request, @PathVariable Long kitchenId,
            @RequestBody @Validated DishReq req) {
        return ApiResponse.ok(dishService.create(userId(request), kitchenId, req));
    }

    @GetMapping("/kitchens/{kitchenId}/dishes")
    public ApiResponse<List<DishView>> list(
            HttpServletRequest request, @PathVariable Long kitchenId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "order") String mode) {
        Long userId = userId(request);
        List<DishView> list = "manage".equals(mode)
                ? dishService.listManage(userId, kitchenId)
                : dishService.listMenu(userId, kitchenId, categoryId, keyword);
        return ApiResponse.ok(list);
    }

    @GetMapping("/kitchens/{kitchenId}/recycle")
    public ApiResponse<List<DishView>> recycle(
            HttpServletRequest request, @PathVariable Long kitchenId) {
        return ApiResponse.ok(dishService.listRecycle(userId(request), kitchenId));
    }

    @GetMapping("/dishes/{id}")
    public ApiResponse<DishView> detail(HttpServletRequest request, @PathVariable Long id) {
        return ApiResponse.ok(dishService.detail(userId(request), id));
    }

    @PutMapping("/dishes/{id}")
    public ApiResponse<DishView> update(
            HttpServletRequest request, @PathVariable Long id,
            @RequestBody @Validated DishReq req) {
        return ApiResponse.ok(dishService.update(userId(request), id, req));
    }

    @PatchMapping("/dishes/{id}/status")
    public ApiResponse<DishView> updateStatus(
            HttpServletRequest request, @PathVariable Long id,
            @RequestBody @Validated StatusReq req) {
        return ApiResponse.ok(dishService.updateStatus(userId(request), id, req.status()));
    }

    @DeleteMapping("/dishes/{id}")
    public ApiResponse<Void> recycleDish(HttpServletRequest request, @PathVariable Long id) {
        dishService.recycle(userId(request), id);
        return ApiResponse.ok();
    }

    @PostMapping("/dishes/{id}/restore")
    public ApiResponse<DishView> restore(HttpServletRequest request, @PathVariable Long id) {
        return ApiResponse.ok(dishService.restore(userId(request), id));
    }

    private Long userId(HttpServletRequest request) {
        return (Long) request.getAttribute(JwtAuthFilter.ATTR_USER_ID);
    }
}
