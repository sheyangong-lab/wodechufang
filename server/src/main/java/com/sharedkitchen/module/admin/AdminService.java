package com.sharedkitchen.module.admin;

import com.sharedkitchen.common.JwtService;
import com.sharedkitchen.common.BusinessException;
import com.sharedkitchen.module.kitchen.Kitchen;
import com.sharedkitchen.module.kitchen.KitchenMemberRepository;
import com.sharedkitchen.module.kitchen.KitchenRepository;
import com.sharedkitchen.module.user.User;
import com.sharedkitchen.module.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 后台管理：厨房管理 + 用户管理。 */
@Service
public class AdminService {

    private final AdminUserRepository adminUserRepository;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final KitchenRepository kitchenRepository;
    private final KitchenMemberRepository memberRepository;

    public AdminService(AdminUserRepository adminUserRepository,
                        JwtService jwtService,
                        UserRepository userRepository,
                        KitchenRepository kitchenRepository,
                        KitchenMemberRepository memberRepository) {
        this.adminUserRepository = adminUserRepository;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.kitchenRepository = kitchenRepository;
        this.memberRepository = memberRepository;
    }

    // ---------- 登录 ----------

    public String login(String username, String password) {
        AdminUser admin = adminUserRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(401, "账号或密码错误"));
        if (!hash(password).equals(admin.getPasswordHash())) {
            throw new BusinessException(401, "账号或密码错误");
        }
        // subject 前缀 admin: 与普通用户 token 区分，AdminAuthFilter 据此校验
        return jwtService.issueAdmin(admin.getId());
    }

    // ---------- 概览 ----------

    public Overview overview() {
        long users = userRepository.count();
        long kitchens = kitchenRepository.count();
        return new Overview(users, kitchens);
    }

    // ---------- 厨房 ----------

    public List<KitchenAdminView> kitchens(String keyword) {
        List<Kitchen> all = kitchenRepository.findAll();
        return all.stream()
                .filter(k -> match(keyword, k.getName(), k.getCode()))
                .map(this::toKitchenView)
                .toList();
    }

    public KitchenAdminView kitchenDetail(Long id) {
        return toKitchenView(kitchenRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "厨房不存在")));
    }

    @Transactional
    public KitchenAdminView setBan(Long kitchenId, boolean ban) {
        Kitchen k = kitchenRepository.findById(kitchenId)
                .orElseThrow(() -> new BusinessException(404, "厨房不存在"));
        k.setStatus(ban ? 0 : 1);
        kitchenRepository.save(k);
        return toKitchenView(k);
    }

    private KitchenAdminView toKitchenView(Kitchen k) {
        User owner = userRepository.findById(k.getOwnerId()).orElse(null);
        return new KitchenAdminView(
                k.getId(), k.getName(), k.getCode(),
                owner == null ? "" : owner.getNickname(),
                memberRepository.countByKitchenId(k.getId()),
                k.getStatus());
    }

    // ---------- 用户 ----------

    public List<UserAdminView> users(String keyword) {
        return userRepository.findAll().stream()
                .filter(u -> match(keyword, u.getNickname(), u.getPhone(), u.getOpenId()))
                .map(u -> new UserAdminView(u.getId(), u.getNickname(), u.getPhone(),
                        u.getPoints(), u.getStatus(), u.getCreatedAt()))
                .toList();
    }

    @Transactional
    public void setUserBan(Long userId, boolean ban) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));
        u.setStatus(ban ? 0 : 1);
        userRepository.save(u);
    }

    /** 赠送积分。 */
    @Transactional
    public void grantPoints(Long userId, long points) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));
        u.setPoints(Math.max(0, u.getPoints() + (int) points));
        userRepository.save(u);
    }

    private boolean match(String keyword, String... fields) {
        if (keyword == null || keyword.isBlank()) return true;
        for (String f : fields) {
            if (f != null && f.contains(keyword.trim())) return true;
        }
        return false;
    }

    private String hash(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest((password + "|shared-kitchen").getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public record Overview(long users, long kitchens) {}

    public record KitchenAdminView(Long id, String name, String code, String ownerNickname,
                                   long memberCount, Integer status) {}

    public record UserAdminView(Long id, String nickname, String phone,
                                Integer points, Integer status, String createdAt) {}
}
