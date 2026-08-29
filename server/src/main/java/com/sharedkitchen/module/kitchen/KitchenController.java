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

    public record MemberUpdateReq(String alias, String title, Integer fullAccess) {}

    /** 编辑成员：自定义名字/职称；主账号可额外设置全权限。 */
    @org.springframework.web.bind.annotation.PutMapping("/{id}/members/{userId}")
    public ApiResponse<KitchenService.MemberView> updateMember(
            HttpServletRequest request, @PathVariable Long id, @PathVariable Long userId,
            @RequestBody MemberUpdateReq req) {
        Long me = (Long) request.getAttribute(JwtAuthFilter.ATTR_USER_ID);
        return ApiResponse.ok(kitchenService.updateMember(
                me, id, userId, req.alias(), req.title(), req.fullAccess()));
    }
}
