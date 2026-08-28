package com.sharedkitchen.module.user;

import com.sharedkitchen.common.ApiResponse;
import com.sharedkitchen.common.JwtAuthFilter;
import com.sharedkitchen.module.auth.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping
    public ApiResponse<UserView> me(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(JwtAuthFilter.ATTR_USER_ID);
        return ApiResponse.ok(authService.me(userId));
    }
}
