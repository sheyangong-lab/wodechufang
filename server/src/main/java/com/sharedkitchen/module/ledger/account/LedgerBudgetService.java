package com.sharedkitchen.module.ledger.account;

import com.sharedkitchen.common.BusinessException;
import com.sharedkitchen.module.fridge.Notification;
import com.sharedkitchen.module.fridge.NotificationRepository;
import com.sharedkitchen.module.kitchen.KitchenMember;
import com.sharedkitchen.module.kitchen.KitchenMemberRepository;
import com.sharedkitchen.module.ledger.LedgerEntry;
import com.sharedkitchen.module.ledger.LedgerEntryRepository;
import com.sharedkitchen.module.ledger.LedgerService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 月度预算：总预算 + 分类预算，每厨房每月一组、PUT 整体覆盖保存。
 * 用量 = 当月 EXPENSE 流水合计（TRANSFER 是钱包间搬家不计）；复制上月由前端 GET 上月后 PUT 本月实现。
 */
@Service
public class LedgerBudgetService {

    private static final int MAX_ITEM_CATEGORIES = 40;

    private final BudgetRepository budgetRepository;
    private final LedgerEntryRepository ledgerRepository;
    private final NotificationRepository notificationRepository;
    private final KitchenMemberRepository memberRepository;
    private final LedgerService ledgerService;

    public LedgerBudgetService(BudgetRepository budgetRepository,
                               LedgerEntryRepository ledgerRepository,
                               NotificationRepository notificationRepository,
                               KitchenMemberRepository memberRepository,
                               LedgerService ledgerService) {
        this.budgetRepository = budgetRepository;
        this.ledgerRepository = ledgerRepository;
        this.notificationRepository = notificationRepository;
        this.memberRepository = memberRepository;
        this.ledgerService = ledgerService;
    }

    /** 当月预算（总+分类）与实际用量；未设总预算时 total 为 null。 */
    @Transactional
    public BudgetView get(Long userId, Long kitchenId, String month) {
        requireMember(kitchenId, userId);
        return assemble(kitchenId, validMonth(month));
    }

    /** 整体覆盖保存：先清当月旧行再插入。totalFen≤0 = 清除总预算；items 里 amountFen=0 = 删除该项。回执回显落库结果。 */
    @Transactional
    public BudgetPutView put(Long userId, Long kitchenId, BudgetPutReq req) {
        requireMember(kitchenId, userId);
        if (req == null || req.month() == null || !req.month().trim().matches("\\d{4}-\\d{2}")) {
            throw new BusinessException("月份格式应为 YYYY-MM");
        }
        String m = req.month().trim();
        Map<String, Long> itemMap = new LinkedHashMap<>();
        if (req.items() != null) {
            for (BudgetItemReq item : req.items()) {
                if (item == null || item.category() == null || item.category().isBlank()) {
                    continue; // 空行忽略
                }
                String cat = item.category().trim();
                long amount = item.amountFen() == null ? 0L : item.amountFen();
                if (amount < 0) {
                    throw new BusinessException("预算金额不能为负数");
                }
                if (itemMap.containsKey(cat)) {
                    throw new BusinessException("分类预算重复：" + cat);
                }
                itemMap.put(cat, amount);
            }
        }
        if (itemMap.size() > MAX_ITEM_CATEGORIES) {
            throw new BusinessException("分类预算最多 " + MAX_ITEM_CATEGORIES + " 项");
        }
        if (!itemMap.isEmpty()) { // 分类须是本厨房的支出分类（首次访问懒加载默认分类）
            Set<String> expenseCats = ledgerService.listCategories(userId, kitchenId).stream()
                    .filter(c -> LedgerEntry.TYPE_EXPENSE.equals(c.type()))
                    .map(LedgerService.LedgerCategoryView::name)
                    .collect(Collectors.toSet());
            for (String cat : itemMap.keySet()) {
                if (!expenseCats.contains(cat)) {
                    throw new BusinessException("不支持的分类：" + cat);
                }
            }
        }
        budgetRepository.deleteByKitchenIdAndMonth(kitchenId, m);
        String t = now();
        long savedTotal = 0;
        if (req.totalFen() != null && req.totalFen() > 0) {
            savedTotal = req.totalFen();
            insert(kitchenId, m, null, savedTotal, t);
        }
        List<BudgetItemReq> savedItems = new ArrayList<>();
        for (Map.Entry<String, Long> e : itemMap.entrySet()) {
            if (e.getValue() > 0) {
                insert(kitchenId, m, e.getKey(), e.getValue(), t);
                savedItems.add(new BudgetItemReq(e.getKey(), e.getValue()));
            }
        }
        return new BudgetPutView(m, savedTotal, savedItems);
    }

    /**
     * 预算超支提醒（定时任务顺带）：当月总预算 used>=total 且未提醒过
     * （title="预算超支 YYYY-MM" 判重）→ 插入 type=BUDGET 通知。单厨房失败不影响其他厨房。
     */
    public int notifyOverruns() {
        String m = LocalDate.now().toString().substring(0, 7);
        int inserted = 0;
        for (Long kitchenId : budgetRepository.findKitchenIdsByMonth(m)) {
            try {
                inserted += notifyOverrun(kitchenId, m);
            } catch (Exception ignore) { /* 尽力而为，明日再试 */ }
        }
        return inserted;
    }

    private int notifyOverrun(Long kitchenId, String m) {
        long total = 0;
        for (Budget b : budgetRepository.findByKitchenIdAndMonth(kitchenId, m)) {
            if (b.getCategory() == null) {
                total = b.getAmountFen();
                break;
            }
        }
        if (total <= 0) {
            return 0;
        }
        long used = monthExpenseTotal(kitchenId, m);
        if (used < total) {
            return 0;
        }
        String title = "预算超支 " + m;
        if (notificationRepository.existsByKitchenIdAndTitle(kitchenId, title)) {
            return 0;
        }
        Notification n = new Notification();
        n.setKitchenId(kitchenId);
        n.setType(Notification.TYPE_BUDGET);
        n.setTitle(title);
        n.setContent("本月支出¥" + yuan(used) + " 已超过预算¥" + yuan(total));
        n.setIsRead(0);
        n.setCreatedAt(now());
        notificationRepository.save(n);
        return 1;
    }

    // ---------- 内部 ----------

    private BudgetView assemble(Long kitchenId, String m) {
        List<BudgetItemView> items = new ArrayList<>();
        BudgetTotalView total = null;
        Map<String, Long> usedByCategory = usedByCategory(kitchenId, m);
        long totalUsed = 0;
        for (Long v : usedByCategory.values()) {
            totalUsed += v;
        }
        for (Budget b : budgetRepository.findByKitchenIdAndMonth(kitchenId, m)) {
            if (b.getCategory() == null) {
                total = new BudgetTotalView(b.getAmountFen(), totalUsed, pct(totalUsed, b.getAmountFen()));
            } else {
                long used = usedByCategory.getOrDefault(b.getCategory(), 0L);
                items.add(new BudgetItemView(b.getCategory(), b.getAmountFen(), used, pct(used, b.getAmountFen())));
            }
        }
        return new BudgetView(m, total, items);
    }

    private void insert(Long kitchenId, String month, String category, long amountFen, String t) {
        Budget b = new Budget();
        b.setKitchenId(kitchenId);
        b.setMonth(month);
        b.setCategory(category);
        b.setAmountFen(amountFen);
        b.setCreatedAt(t);
        budgetRepository.save(b);
    }

    /** 当月各分类支出合计（key=分类名）。 */
    private Map<String, Long> usedByCategory(Long kitchenId, String m) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (Object[] row : ledgerRepository.sumExpenseByCategory(kitchenId, m + "%")) {
            long sum = row[1] == null ? 0L : ((Number) row[1]).longValue();
            map.put((String) row[0], sum);
        }
        return map;
    }

    /** 当月支出总额（各分类求和）。 */
    private long monthExpenseTotal(Long kitchenId, String m) {
        long sum = 0;
        for (Long v : usedByCategory(kitchenId, m).values()) {
            sum += v;
        }
        return sum;
    }

    private int pct(long used, long budgetFen) {
        if (budgetFen <= 0) {
            return used > 0 ? 100 : 0;
        }
        return (int) Math.min(used * 100 / budgetFen, 999); // 超支时如实 >100，封顶防脏数据
    }

    private String yuan(long fen) {
        return BigDecimal.valueOf(fen, 2).toPlainString();
    }

    private String validMonth(String month) {
        if (month == null || month.isBlank()) return LocalDate.now().toString().substring(0, 7);
        if (!month.trim().matches("\\d{4}-\\d{2}")) {
            throw new BusinessException("月份格式应为 YYYY-MM");
        }
        return month.trim();
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

    // ---------- 视图 / 请求 ----------

    public record BudgetTotalView(Long budgetFen, Long usedFen, Integer pct) {}

    public record BudgetItemView(String category, Long budgetFen, Long usedFen, Integer pct) {}

    public record BudgetView(String month, BudgetTotalView total, List<BudgetItemView> items) {}

    public record BudgetItemReq(String category, Long amountFen) {}

    public record BudgetPutReq(String month, Long totalFen, List<BudgetItemReq> items) {}

    /** PUT 保存回执：按请求结构回显实际落库结果（清除的总预算/删掉的项不回显）。 */
    public record BudgetPutView(String month, Long totalFen, List<BudgetItemReq> items) {}
}
