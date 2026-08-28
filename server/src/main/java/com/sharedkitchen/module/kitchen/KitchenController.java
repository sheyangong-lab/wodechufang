package com.sharedkitchen.module.kitchen;

import com.sharedkitchen.common.ApiResponse;
import com.sharedkitchen.common.JwtAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/kitchens")
@Validated
public class KitchenController {

    private final KitchenService kitchenService;

    public KitchenController(KitchenService kitchenService) {
        this.kitchenService = kitchenService;
    }

    public record CreateReq(@NotBlank String name) {}

    public record JoinReq(@NotBlank String code) {}

    @PostMapping
    public ApiResponse<KitchenService.KitchenView> create(
            HttpServletRequest request, @RequestBody @Validated CreateReq req) {
        Long userId = (Long) request.getAttribute(JwtAuthFilter.ATTR_USER_ID);
        return ApiResponse.ok(kitchenService.create(userId, req.name()));
    }

    @PostMapping("/join")
    public ApiResponse<KitchenService.KitchenView> join(
            HttpServletRequest request, @RequestBody @Validated JoinReq req) {
        Long userId = (Long) request.getAttribute(JwtAuthFilter.ATTR_USER_ID);
        return ApiResponse.ok(kitchenService.join(userId, req.code()));
    }

    @GetMapping("/mine")
    public ApiResponse<List<KitchenService.KitchenView>> mine(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(JwtAuthFilter.ATTR_USER_ID);
        return ApiResponse.ok(kitchenService.myKitchens(userId));
    }

    @GetMapping("/{id}")
    public ApiResponse<KitchenService.KitchenDetail> detail(
            HttpServletRequest request, @PathVariable Long id) {
        Long userId = (Long) request.getAttribute(JwtAuthFilter.ATTR_USER_ID);
        return ApiResponse.ok(kitchenService.detail(userId, id));
    }

    public record UpdateReq(String name, String announcement) {}

    @org.springframework.web.bind.annotation.PutMapping("/{id}")
    public ApiResponse<KitchenService.KitchenView> update(
            HttpServletRequest request, @PathVariable Long id,
            @RequestBody UpdateReq req) {
        Long userId = (Long) request.getAttribute(JwtAuthFilter.ATTR_USER_ID);
        return ApiResponse.ok(kitchenService.update(userId, id, req.name(), req.announcement()));
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{id}")
    public ApiResponse<Void> dissolve(HttpServletRequest request, @PathVariable Long id) {
        Long userId = (Long) request.getAttribute(JwtAuthFilter.ATTR_USER_ID);
        kitchenService.dissolve(userId, id);
        return ApiResponse.ok();
    }

    public record RoleReq(@jakarta.validation.constraints.NotBlank String role) {}

    @org.springframework.web.bind.annotation.PutMapping("/{kitchenId}/members/{userId}/role")
    public ApiResponse<KitchenService.MemberView> setMemberRole(
            HttpServletRequest request, @PathVariable Long kitchenId,
            @PathVariable Long userId, @RequestBody @Validated RoleReq req) {
        Long operator = (Long) request.getAttribute(JwtAuthFilter.ATTR_USER_ID);
        return ApiResponse.ok(kitchenService.setMemberRole(operator, kitchenId, userId, req.role()));
    }
}
