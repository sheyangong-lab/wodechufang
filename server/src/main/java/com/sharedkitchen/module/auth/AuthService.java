package com.sharedkitchen.module.auth;

import com.sharedkitchen.common.BusinessException;
import com.sharedkitchen.common.JwtService;
import com.sharedkitchen.module.user.User;
import com.sharedkitchen.module.user.UserRepository;
import com.sharedkitchen.module.user.UserView;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Random;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 登录注册（M1-T5）：手机号 + 短信验证码。
 * 开发环境使用万能码（auth.dev-sms-code，默认 1234）；接入真实短信通道前禁止用于生产。
 */
@Service
public class AuthService {

    private static final String PHONE_REGEX = "^1\\d{10}$";
    private static final String OPEN_ID_CHARS = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final String devSmsCode;
    private final Random random = new SecureRandom();

    public AuthService(UserRepository userRepository,
                       JwtService jwtService,
                       @Value("${auth.dev-sms-code:1234}") String devSmsCode) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.devSmsCode = devSmsCode;
    }

    public void sendSmsCode(String phone) {
        requireValidPhone(phone);
        // TODO(M6 前后)：接入真实短信通道；当前任何手机号都提示发送成功
    }

    @Transactional
    public LoginResult register(String phone, String smsCode, String nickname) {
        requireValidPhone(phone);
        requireSmsCode(smsCode);
        if (userRepository.existsByPhone(phone)) {
            throw new BusinessException("该手机号已注册，请直接登录");
        }
        User user = new User();
        user.setPhone(phone);
        user.setNickname((nickname == null || nickname.isBlank())
                ? "用户" + (1000 + random.nextInt(9000))
                : nickname.trim());
        user.setOpenId(randomOpenId());
        user.setCreatedAt(Instant.now().toString());
        userRepository.save(user);
        return issue(user);
    }

    public LoginResult login(String phone, String smsCode) {
        requireValidPhone(phone);
        requireSmsCode(smsCode);
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new BusinessException("该手机号未注册，请先注册"));
        if (user.getStatus() != 1) {
            throw new BusinessException(403, "账号已被封禁，请联系客服");
        }
        return issue(user);
    }

    public UserView me(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(401, "用户不存在"));
        return UserView.of(user);
    }

    private LoginResult issue(User user) {
        return new LoginResult(jwtService.issue(user.getId()), UserView.of(user));
    }

    private void requireValidPhone(String phone) {
        if (phone == null || !phone.matches(PHONE_REGEX)) {
            throw new BusinessException("请输入正确的11位手机号");
        }
    }

    private void requireSmsCode(String smsCode) {
        if (smsCode == null || smsCode.isBlank()) {
            throw new BusinessException("请输入验证码");
        }
        if (!devSmsCode.equals(smsCode.trim())) {
            throw new BusinessException("验证码错误");
        }
    }

    private String randomOpenId() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(OPEN_ID_CHARS.charAt(random.nextInt(OPEN_ID_CHARS.length())));
        }
        return sb.toString();
    }

    public record LoginResult(String token, UserView user) {}
}
