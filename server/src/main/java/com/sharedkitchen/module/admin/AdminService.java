package com.sharedkitchen.module.admin;

import com.sharedkitchen.common.JwtService;
import com.sharedkitchen.common.BusinessException;
import com.sharedkitchen.module.vip.RedeemCode;
import com.sharedkitchen.module.vip.RedeemCodeRepository;
import com.sharedkitchen.module.vip.VipPlanRepository;
import com.sharedkitchen.module.kitchen.Kitchen;
import com.sharedkitchen.module.kitchen.KitchenMember;
import com.sharedkitchen.module.kitchen.KitchenMemberRepository;
import com.sharedkitchen.module.kitchen.KitchenRepository;
import com.sharedkitchen.module.user.User;
import com.sharedkitchen.module.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Random;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 后台管理：VIP 账号（厨房会员）+ 厨房管理 + 用户管理 + 兑换码生成。 */
@Service
public class AdminService {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final AdminUserRepository adminUserRepository;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final KitchenRepository kitchenRepository;
    private final KitchenMemberRepository memberRepository;
    private final RedeemCodeRepository codeRepository;
    private final com.sharedkitchen.module.vip.VipPlanRepository planRepository;
    private final Random random = new SecureRandom();

    public AdminService(AdminUserRepository adminUserRepository,
                        JwtService jwtService,
                        UserRepository userRepository,
                        KitchenRepository kitchenRepository,
                        KitchenMemberRepository memberRepository,
                        RedeemCodeRepository codeRepository,
                        com.sharedkitchen.module.vip.VipPlanRepository planRepository) {
        this.adminUserRepository = adminUserRepository;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.kitchenRepository = kitchenRepository;
        this.memberRepository = memberRepository;
        this.codeRepository = codeRepository;
        this.planRepository = planRepository;
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
        long vipKitchens = kitchenRepository.findAll().stream()
                .filter(com.sharedkitchen.module.vip.VipService::isVip).count();
        long codesTotal = codeRepository.count();
        long codesUsed = codeRepository.findAll().stream()
                .filter(c -> RedeemCode.ST_USED.equals(c.getStatus())).count();
        return new Overview(users, kitchens, vipKitchens, codesTotal, codesUsed);
    }

    // ---------- 厨房（含 VIP 管理） ----------

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

    /** 开通/续期厨房会员 N 天。 */
    @Transactional
    public KitchenAdminView grantVip(Long kitchenId, int days) {
        Kitchen k = kitchenRepository.findById(kitchenId)
                .orElseThrow(() -> new BusinessException(404, "厨房不存在"));
        LocalDate today = LocalDate.now();
        LocalDate current = k.getVipExpireAt() == null ? null
                : LocalDate.parse(k.getVipExpireAt().substring(0, 10));
        LocalDate base = current != null && current.isAfter(today) ? current : today;
        k.setVipExpireAt(base.plusDays(days).atTime(23, 59)
                .atZone(ZoneId.of("Asia/Shanghai")).toInstant().toString());
        kitchenRepository.save(k);
        return toKitchenView(k);
    }

    /** 关闭厨房会员。 */
    @Transactional
    public KitchenAdminView revokeVip(Long kitchenId) {
        Kitchen k = kitchenRepository.findById(kitchenId)
                .orElseThrow(() -> new BusinessException(404, "厨房不存在"));
        k.setVipExpireAt(null);
        kitchenRepository.save(k);
        return toKitchenView(k);
    }

    /** 调整免费额度。 */
    @Transactional
    public KitchenAdminView setQuota(Long kitchenId, Integer dishQuota, Integer categoryQuota) {
        Kitchen k = kitchenRepository.findById(kitchenId)
                .orElseThrow(() -> new BusinessException(404, "厨房不存在"));
        if (dishQuota != null && dishQuota > 0) k.setDishQuota(dishQuota);
        if (categoryQuota != null && categoryQuota > 0) k.setCategoryQuota(categoryQuota);
        kitchenRepository.save(k);
        return toKitchenView(k);
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
        boolean vip = com.sharedkitchen.module.vip.VipService.isVip(k);
        return new KitchenAdminView(
                k.getId(), k.getName(), k.getCode(),
                owner == null ? "" : owner.getNickname(),
                memberRepository.countByKitchenId(k.getId()),
                vip, k.getVipExpireAt() == null ? "" : k.getVipExpireAt().substring(0, 10),
                k.getDishQuota(), k.getCategoryQuota(),
                com.sharedkitchen.module.vip.VipService.effectiveDishQuota(k),
                com.sharedkitchen.module.vip.VipService.effectiveCategoryQuota(k),
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

    // ---------- 兑换码 ----------

    /** 批量生成兑换码，返回码表文本（每行一个）。 */
    @Transactional
    public String generateCodes(Long planId, int count, String batch) {
        if (planRepository.findById(planId).isEmpty()) {
            throw new BusinessException("套餐不存在");
        }
        if (count < 1 || count > 1000) {
            throw new BusinessException("单次生成 1-1000 个");
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            RedeemCode rc = new RedeemCode();
            rc.setCode(generateCode());
            rc.setPlanId(planId);
            rc.setBatch(batch == null || batch.isBlank() ? "B" + LocalDate.now().toString().replace("-", "") : batch.trim());
            rc.setCreatedAt(Instant.now().toString());
            codeRepository.save(rc);
            sb.append(rc.getCode()).append('\n');
        }
        return sb.toString();
    }

    @Transactional
    public void disableCode(Long codeId) {
        RedeemCode rc = codeRepository.findById(codeId)
                .orElseThrow(() -> new BusinessException(404, "兑换码不存在"));
        rc.setStatus(RedeemCode.ST_DISABLED);
        codeRepository.save(rc);
    }

    public List<CodeAdminView> codes(String batch) {
        List<RedeemCode> list = batch == null || batch.isBlank()
                ? codeRepository.findAll()
                : codeRepository.findByBatchOrderByCreatedAtDesc(batch.trim());
        return list.stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(200)
                .map(c -> new CodeAdminView(c.getId(), c.getCode(), c.getPlanId(),
                        c.getStatus(), c.getBatch(),
                        c.getUsedKitchenId() == null ? "" : String.valueOf(c.getUsedKitchenId())))
                .toList();
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(16);
        do {
            sb.setLength(0);
            for (int i = 0; i < 16; i++) {
                sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
            }
        } while (codeRepository.findByCode(sb.toString()).isPresent());
        // 4-4-4-4 分组便于阅读
        return sb.substring(0, 4) + "-" + sb.substring(4, 8) + "-" + sb.substring(8, 12) + "-" + sb.substring(12);
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

    public record Overview(long users, long kitchens, long vipKitchens,
                           long codesTotal, long codesUsed) {}

    public record KitchenAdminView(Long id, String name, String code, String ownerNickname,
                                   long memberCount, boolean vip, String vipExpireDate,
                                   Integer dishQuota, Integer categoryQuota,
                                   Integer effectiveDishQuota, Integer effectiveCategoryQuota,
                                   Integer status) {}

    public record UserAdminView(Long id, String nickname, String phone,
                                Integer points, Integer status, String createdAt) {}

    public record CodeAdminView(Long id, String code, Long planId, String status,
                                String batch, String usedKitchenId) {}
}
