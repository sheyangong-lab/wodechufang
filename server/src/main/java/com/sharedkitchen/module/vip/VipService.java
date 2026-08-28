package com.sharedkitchen.module.vip;

import com.sharedkitchen.common.BusinessException;
import com.sharedkitchen.module.kitchen.Kitchen;
import com.sharedkitchen.module.kitchen.KitchenMember;
import com.sharedkitchen.module.kitchen.KitchenMemberRepository;
import com.sharedkitchen.module.kitchen.KitchenRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VipService {

    /** 会员额度（Global Constraints：免费 50 菜/5 分类 → 会员 500/50）。 */
    public static final int VIP_DISH_QUOTA = 500;
    public static final int VIP_CATEGORY_QUOTA = 50;

    private final VipPlanRepository planRepository;
    private final RedeemCodeRepository codeRepository;
    private final KitchenRepository kitchenRepository;
    private final KitchenMemberRepository memberRepository;

    public VipService(VipPlanRepository planRepository,
                      RedeemCodeRepository codeRepository,
                      KitchenRepository kitchenRepository,
                      KitchenMemberRepository memberRepository) {
        this.planRepository = planRepository;
        this.codeRepository = codeRepository;
        this.kitchenRepository = kitchenRepository;
        this.memberRepository = memberRepository;
    }

    /** 会员是否生效：到期时间非空且晚于当前。 */
    public static boolean isVip(Kitchen kitchen) {
        return kitchen.getVipExpireAt() != null
                && kitchen.getVipExpireAt().compareTo(Instant.now().toString()) > 0;
    }

    public static int effectiveDishQuota(Kitchen kitchen) {
        return isVip(kitchen) ? VIP_DISH_QUOTA : kitchen.getDishQuota();
    }

    public static int effectiveCategoryQuota(Kitchen kitchen) {
        return isVip(kitchen) ? VIP_CATEGORY_QUOTA : kitchen.getCategoryQuota();
    }

    public List<PlanView> listPlans() {
        return planRepository.findByEnabledTrueOrderBySortAsc().stream()
                .map(p -> new PlanView(p.getId(), p.getName(), p.getDurationDays(), p.getPriceFen()))
                .toList();
    }

    /**
     * 兑换码开通/续期厨房会员（店长专用）。
     * 到期顺延规则：未开通或已过期 → 从明天起算；生效中 → 从当前到期日起顺延。
     */
    @Transactional
    public VipStatus redeem(Long userId, Long kitchenId, String code) {
        requireOwner(kitchenId, userId);
        if (code == null || code.isBlank()) {
            throw new BusinessException("请输入兑换码");
        }
        RedeemCode rc = codeRepository.findByCode(code.trim())
                .orElseThrow(() -> new BusinessException("兑换码不存在，请核对后重试"));
        if (RedeemCode.ST_USED.equals(rc.getStatus())) {
            throw new BusinessException("该兑换码已被使用");
        }
        if (RedeemCode.ST_DISABLED.equals(rc.getStatus())) {
            throw new BusinessException("该兑换码已作废");
        }
        VipPlan plan = planRepository.findById(rc.getPlanId())
                .orElseThrow(() -> new BusinessException("兑换码对应的套餐不存在"));

        Kitchen kitchen = kitchenRepository.findById(kitchenId)
                .orElseThrow(() -> new BusinessException(404, "厨房不存在"));
        LocalDate today = LocalDate.now();
        LocalDate currentExpire = kitchen.getVipExpireAt() == null ? null
                : LocalDate.parse(kitchen.getVipExpireAt().substring(0, 10));
        LocalDate base = currentExpire != null && currentExpire.isAfter(today) ? currentExpire : today;
        LocalDate newExpire = base.plusDays(plan.getDurationDays());
        kitchen.setVipExpireAt(newExpire.atTime(23, 59)
                .atZone(ZoneId.of("Asia/Shanghai")).toInstant().toString());
        kitchenRepository.save(kitchen);

        rc.setStatus(RedeemCode.ST_USED);
        rc.setUsedBy(userId);
        rc.setUsedKitchenId(kitchenId);
        rc.setUsedAt(Instant.now().toString());
        codeRepository.save(rc);

        return status(kitchenId);
    }

    /** 支付购买（预留接口）：接入微信支付/华为 IAP 前引导使用兑换码。 */
    public void purchase(Long userId, Long kitchenId, Long planId) {
        requireOwner(kitchenId, userId);
        throw new BusinessException("在线支付即将开放，请先使用兑换码开通会员");
    }

    public VipStatus status(Long kitchenId) {
        Kitchen kitchen = kitchenRepository.findById(kitchenId)
                .orElseThrow(() -> new BusinessException(404, "厨房不存在"));
        boolean vip = isVip(kitchen);
        String expireDate = kitchen.getVipExpireAt() == null ? null
                : kitchen.getVipExpireAt().substring(0, 10);
        return new VipStatus(vip, expireDate,
                effectiveDishQuota(kitchen), effectiveCategoryQuota(kitchen), listPlans());
    }

    private void requireOwner(Long kitchenId, Long userId) {
        KitchenMember m = memberRepository.findByKitchenIdAndUserId(kitchenId, userId)
                .orElseThrow(() -> new BusinessException(403, "你还不是该厨房的成员"));
        if (!KitchenMember.ROLE_OWNER.equals(m.getRole())) {
            throw new BusinessException(403, "只有店长可以开通会员");
        }
    }

    public record PlanView(Long id, String name, Integer durationDays, Long priceFen) {}

    public record VipStatus(boolean isVip, String expireDate,
                            int dishQuota, int categoryQuota, List<PlanView> plans) {}
}
