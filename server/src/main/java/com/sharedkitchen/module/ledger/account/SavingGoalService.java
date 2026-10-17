package com.sharedkitchen.module.ledger.account;

import com.sharedkitchen.common.BusinessException;
import com.sharedkitchen.module.kitchen.KitchenMember;
import com.sharedkitchen.module.kitchen.KitchenMemberRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 存款目标：建议单一目标但允许多个；progress 为全部 SAVING 账户余额之和（各目标共用同一进度），
 * 余额复用 AccountService.list（含期初 + 流水累计），不另写聚合。
 */
@Service
public class SavingGoalService {

    private final SavingGoalRepository goalRepository;
    private final AccountService accountService;
    private final KitchenMemberRepository memberRepository;

    public SavingGoalService(SavingGoalRepository goalRepository,
                             AccountService accountService,
                             KitchenMemberRepository memberRepository) {
        this.goalRepository = goalRepository;
        this.accountService = accountService;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public List<GoalView> list(Long userId, Long kitchenId) {
        requireMember(kitchenId, userId);
        long progress = savingBalance(userId, kitchenId);
        return goalRepository.findByKitchenIdAndDeletedOrderByCreatedAtDescIdDesc(kitchenId, 0)
                .stream().map(g -> toView(g, progress)).toList();
    }

    @Transactional
    public GoalView create(Long userId, Long kitchenId, String name, Long targetFen, String deadline) {
        requireMember(kitchenId, userId);
        if (name == null || name.isBlank()) {
            throw new BusinessException("请输入目标名称");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 20) {
            throw new BusinessException("目标名称最多20个字");
        }
        if (targetFen == null || targetFen <= 0) {
            throw new BusinessException("目标金额必须大于 0");
        }
        if (deadline != null && !deadline.isBlank()
                && !deadline.trim().matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new BusinessException("截止日期格式应为 YYYY-MM-DD");
        }
        SavingGoal g = new SavingGoal();
        g.setKitchenId(kitchenId);
        g.setName(trimmed);
        g.setTargetFen(targetFen);
        g.setDeadline(deadline == null || deadline.isBlank() ? null : deadline.trim());
        g.setDeleted(0);
        g.setCreatedAt(now());
        goalRepository.save(g);
        return toView(g, savingBalance(userId, kitchenId));
    }

    @Transactional
    public void delete(Long userId, Long kitchenId, Long goalId) {
        requireMember(kitchenId, userId);
        SavingGoal g = goalRepository.findById(goalId)
                .orElseThrow(() -> new BusinessException(404, "目标不存在"));
        if (!g.getKitchenId().equals(kitchenId)) {
            throw new BusinessException(403, "无权操作该目标");
        }
        g.setDeleted(1);
        goalRepository.save(g);
    }

    /** 全部 SAVING 账户余额之和（复用 AccountService 的余额聚合）。 */
    private long savingBalance(Long userId, Long kitchenId) {
        return accountService.list(userId, kitchenId).stream()
                .filter(a -> Account.TYPE_SAVING.equals(a.type()))
                .mapToLong(a -> a.balanceFen() == null ? 0L : a.balanceFen())
                .sum();
    }

    private GoalView toView(SavingGoal g, long progressFen) {
        long target = g.getTargetFen() == null ? 0L : g.getTargetFen();
        int pct = target > 0 ? (int) Math.min(progressFen * 100 / target, 100) : 0;
        return new GoalView(g.getId(), g.getName(), g.getTargetFen(), g.getDeadline(), progressFen, pct);
    }

    private void requireMember(Long kitchenId, Long userId) {
        KitchenMember m = memberRepository.findByKitchenIdAndUserId(kitchenId, userId)
                .orElseThrow(() -> new BusinessException(403, "你还不是该厨房的成员"));
        if (!KitchenMember.ROLE_OWNER.equals(m.getRole())
                && !KitchenMember.ROLE_MEMBER.equals(m.getRole())) {
            throw new BusinessException(403, "需要主账号或成员权限");
        }
    }

    private String now() {
        return java.time.Instant.now().toString();
    }

    public record GoalView(Long id, String name, Long targetFen, String deadline,
                           Long progressFen, Integer pct) {}
}
