package com.sharedkitchen.module.ledger;

import com.sharedkitchen.common.BusinessException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 旧存钱罐（v1）兼容桩：v2 已升级为账户体系（SAVING 账户 + TRANSFER 存取，V19 已迁完历史数据）。
 * 读接口恒返回空/全 0，写接口一律拒绝并引导升级；视图类型保留以兼容旧 App 的响应结构。
 */
@Service
public class SavingService {

    private static final String UPGRADE_HINT = "存钱罐已升级为存款账户，请更新 App 到 1.6.0+";

    private final com.sharedkitchen.module.kitchen.KitchenMemberRepository memberRepository;

    public SavingService(com.sharedkitchen.module.kitchen.KitchenMemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    private void requireMember(Long kitchenId, Long userId) {
        var m = memberRepository.findByKitchenIdAndUserId(kitchenId, userId)
                .orElseThrow(() -> new BusinessException(403, "你还不是该厨房的成员"));
        if (!com.sharedkitchen.module.kitchen.KitchenMember.ROLE_OWNER.equals(m.getRole())
                && !com.sharedkitchen.module.kitchen.KitchenMember.ROLE_MEMBER.equals(m.getRole())) {
            throw new BusinessException(403, "需要主账号或成员权限");
        }
    }

    public record SavingView(Long id, String name, String currency, Long amountFen,
                             String kind, String note, String createdAt) {}

    public record SummaryView(Long balanceCnyFen, Long balanceHkdFen, Long balanceUsdFen,
                              List<SavingView> recent) {}

    /** 恒为空列表。 */
    public List<SavingView> list(Long userId, Long kitchenId) {
        requireMember(kitchenId, userId);
        return List.of();
    }

    /** 恒为全 0 汇总。 */
    public Map<String, Object> summary(Long userId, Long kitchenId) {
        requireMember(kitchenId, userId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("balanceCnyFen", 0L);
        out.put("balanceHkdFen", 0L);
        out.put("balanceUsdFen", 0L);
        out.put("recent", List.of());
        return out;
    }

    public SavingView add(Long userId, Long kitchenId, String name, String currency,
                          Long amountFen, String kind, String note) {
        requireMember(kitchenId, userId);
        throw new BusinessException(UPGRADE_HINT);
    }

    public void remove(Long userId, Long savingId) {
        throw new BusinessException(UPGRADE_HINT);
    }
}
