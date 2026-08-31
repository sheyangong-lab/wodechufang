package com.sharedkitchen.module.plan;

import com.sharedkitchen.common.ApiResponse;
import com.sharedkitchen.common.JwtAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
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
@RequestMapping("/api/kitchens/{kitchenId}/plan")
@Validated
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @GetMapping("")
    public ApiResponse<PlanService.PlanDay> day(
            HttpServletRequest request, @PathVariable Long kitchenId,
            @RequestParam(required = false) String date) {
        return ApiResponse.ok(planService.day(userId(request), kitchenId, date));
    }

    public record AddReq(String date, Integer slotIndex, String itemType, Long dishId,
                         String name, String imageUrl, String remark) {}

    @PostMapping("/items")
    public ApiResponse<PlanService.PlanItemView> addItem(
            HttpServletRequest request, @PathVariable Long kitchenId,
            @RequestBody AddReq req) {
        return ApiResponse.ok(planService.addItem(userId(request), kitchenId,
                new PlanService.AddItemReq(req.date(), req.slotIndex(), req.itemType(),
                        req.dishId(), req.name(), req.imageUrl(), req.remark())));
    }

    @DeleteMapping("/items/{itemId}")
    public ApiResponse<Void> removeItem(
            HttpServletRequest request, @PathVariable Long kitchenId, @PathVariable Long itemId) {
        planService.removeItem(userId(request), kitchenId, itemId);
        return ApiResponse.ok();
    }

    public record SlotsReq(@NotBlank List<String> slots) {}

    @GetMapping("/slots")
    public ApiResponse<List<String>> slots(
            HttpServletRequest request, @PathVariable Long kitchenId) {
        return ApiResponse.ok(planService.slots(userId(request), kitchenId));
    }

    @PutMapping("/slots")
    public ApiResponse<List<String>> updateSlots(
            HttpServletRequest request, @PathVariable Long kitchenId,
            @RequestBody SlotsReq req) {
        return ApiResponse.ok(planService.updateSlots(userId(request), kitchenId, req.slots()));
    }

    private Long userId(HttpServletRequest request) {
        return (Long) request.getAttribute(JwtAuthFilter.ATTR_USER_ID);
    }
}
