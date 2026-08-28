package com.sharedkitchen.module.auth;

import com.sharedkitchen.common.ApiResponse;
import com.sharedkitchen.module.auth.AuthService.LoginResult;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Validated
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    public record SmsCodeReq(@NotBlank String phone) {}

    public record RegisterReq(@NotBlank String phone, @NotBlank String smsCode, String nickname) {}

    public record LoginReq(@NotBlank String phone, @NotBlank String smsCode) {}

    @PostMapping("/sms-code")
    public ApiResponse<Void> smsCode(@RequestBody @Validated SmsCodeReq req) {
        authService.sendSmsCode(req.phone());
        return ApiResponse.ok();
    }

    @PostMapping("/register")
    public ApiResponse<LoginResult> register(@RequestBody @Validated RegisterReq req) {
        return ApiResponse.ok(authService.register(req.phone(), req.smsCode(), req.nickname()));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResult> login(@RequestBody @Validated LoginReq req) {
        return ApiResponse.ok(authService.login(req.phone(), req.smsCode()));
    }
}
