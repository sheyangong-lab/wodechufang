package com.sharedkitchen.module.user;

import com.sharedkitchen.common.ApiResponse;
import com.sharedkitchen.common.BusinessException;
import com.sharedkitchen.common.JwtAuthFilter;
import com.sharedkitchen.module.auth.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
@Validated
public class UserController {

    private final AuthService authService;
    private final UserRepository userRepository;

    public UserController(AuthService authService, UserRepository userRepository) {
        this.authService = authService;
        this.userRepository = userRepository;
    }

    public record UpdateMeReq(@NotBlank @Size(max = 20) String nickname) {}

    @GetMapping
    public ApiResponse<UserView> me(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(JwtAuthFilter.ATTR_USER_ID);
        return ApiResponse.ok(authService.me(userId));
    }

    @PatchMapping
    public ApiResponse<UserView> update(HttpServletRequest request,
                                        @RequestBody @Validated UpdateMeReq req) {
        Long userId = (Long) request.getAttribute(JwtAuthFilter.ATTR_USER_ID);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(401, "用户不存在"));
        user.setNickname(req.nickname().trim());
        userRepository.save(user);
        return ApiResponse.ok(UserView.of(user));
    }
}
