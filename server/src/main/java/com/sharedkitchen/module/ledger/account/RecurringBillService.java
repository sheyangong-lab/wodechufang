package com.sharedkitchen.module.ledger.account;

import com.sharedkitchen.common.BusinessException;
import com.sharedkitchen.module.kitchen.KitchenMember;
import com.sharedkitchen.module.kitchen.KitchenMemberRepository;
import com.sharedkitchen.module.ledger.LedgerEntry;
import com.sharedkitchen.module.ledger.LedgerEntryRepository;
import com.sharedkitchen.module.ledger.LedgerService;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 周期账单：房租/水电/工资等固定收支按周期自动入账。
 * 扫描规则：enabled 且 next_date <= today 的账单，while 循环逐周期生成流水（dine_date=next_date）
 * 并推进 next_date，直至越过今天——停机多月也能一次补齐欠账。
 */
@Service
public class RecurringBillService {

    private static final Logger log = LoggerFactory.getLogger(RecurringBillService.class);
    /** 单账单补账安全上限（月缴约 100 年），防脏数据死循环。 */
    private static final int MAX_CATCHUP_CYCLES = 1200;
    /** 扫描防重入：定时任务与手动触发重叠时后来者直接跳过。 */
    private final java.util.concurrent.atomic.AtomicBoolean scanning = new java.util.concurrent.atomic.AtomicBoolean(false);
    /** 单账单补账互斥锁：scanOnce 与建单/改单立即补账共用（进程内，单机部署够用）。 */
    private static final Object SCAN_LOCK = new Object();

    private final RecurringBillRepository billRepository;
    private final LedgerEntryRepository ledgerRepository;
    private final AccountRepository accountRepository;
    private final KitchenMemberRepository memberRepository;
    private final LedgerService ledgerService;
    private final TransactionTemplate transactionTemplate;

    public RecurringBillService(RecurringBillRepository billRepository,
                                LedgerEntryRepository ledgerRepository,
                                AccountRepository accountRepository,
                                KitchenMemberRepository memberRepository,
                                LedgerService ledgerService,
                                TransactionTemplate transactionTemplate) {
        this.billRepository = billRepository;
        this.ledgerRepository = ledgerRepository;
        this.accountRepository = accountRepository;
        this.memberRepository = memberRepository;
        this.ledgerService = ledgerService;
        this.transactionTemplate = transactionTemplate;
    }

    public List<BillView> list(Long userId, Long kitchenId) {
        requireMember(kitchenId, userId);
        return billRepository.findByKitchenIdAndDeletedOrderByNextDateAscIdAsc(kitchenId, 0)
                .stream().map(this::toView).toList();
    }

    /** 建单：校验落库（独立事务）后，起始日已到则锁内立即补账（见 catchUpNow）。 */
    public BillView create(Long userId, Long kitchenId, BillReq req) {
        requireMember(kitchenId, userId);
        RecurringBill b = new RecurringBill();
        b.setKitchenId(kitchenId);
        applyReq(b, kitchenId, userId, req);
        b.setEnabled(req.enabled() == null ? 1 : req.enabled() == 0 ? 0 : 1);
        b.setDeleted(0);
        b.setCreatedAt(now());
        billRepository.save(b);
        if (b.getEnabled() == 1 && b.getNextDate().compareTo(today()) <= 0) {
            catchUpNow(b.getId()); // 历史起始日/当天到账：建单即补账
        }
        return toView(billRepository.findById(b.getId()).orElse(b));
    }

    /** 仅更新传入（非 null）字段；更新落库后 nextDate 已到且启用中则锁内立即补账。 */
    public BillView update(Long userId, Long kitchenId, Long billId, BillReq req) {
        requireMember(kitchenId, userId);
        RecurringBill b = owned(kitchenId, billId);
        applyReq(b, kitchenId, userId, req);
        if (req.enabled() != null) {
            b.setEnabled(req.enabled() == 0 ? 0 : 1);
        }
        billRepository.save(b);
        if (b.getEnabled() == 1 && b.getNextDate().compareTo(today()) <= 0) {
            catchUpNow(b.getId());
        }
        return toView(billRepository.findById(b.getId()).orElse(b));
    }

    @Transactional
    public void delete(Long userId, Long kitchenId, Long billId) {
        requireMember(kitchenId, userId);
        RecurringBill b = owned(kitchenId, billId);
        b.setDeleted(1);
        billRepository.save(b);
    }

    /**
     * 全量扫描（定时任务/手动触发）：逐账单补账，返回本次生成的流水条数。
     * 每账单独立事务（TransactionTemplate），单条失败记日志继续，不影响其他账单与其他厨房。
     * 单账单处理段持 SCAN_LOCK 且事务提交发生在锁内（见 catchUpFresh），
     * 与建单/改单的立即补账路径互斥——同账单不可能双补重复入账。
     * scanning 防重入：定时任务与手动触发重叠时后来者直接跳过。
     */
    public int scanOnce() {
        if (!scanning.compareAndSet(false, true)) {
            return 0;
        }
        try {
            String today = today();
            int entries = 0;
            for (RecurringBill b : billRepository.findByDeletedAndEnabled(0, 1)) {
                if (b.getNextDate() == null || b.getNextDate().compareTo(today) > 0) {
                    continue;
                }
                try {
                    Integer posted;
                    synchronized (SCAN_LOCK) {
                        posted = transactionTemplate.execute(status -> catchUpFresh(b.getId()));
                    }
                    if (posted != null) {
                        entries += posted;
                    }
                } catch (Exception e) {
                    log.warn("周期账 #{}（厨房 {}）补账失败：{}", b.getId(), b.getKitchenId(), e.getMessage());
                }
            }
            return entries;
        } finally {
            scanning.set(false);
        }
    }

    /**
     * 建单/改单后的立即补账：持 SCAN_LOCK 开独立事务补账，提交在锁内——
     * 别的线程（scanOnce）拿到锁时必能读到已提交的最新 next_date，杜绝并发双补。
     */
    private void catchUpNow(Long billId) {
        synchronized (SCAN_LOCK) {
            transactionTemplate.executeWithoutResult(status -> catchUpFresh(billId));
        }
    }

    /**
     * 单账单补账（须在 SCAN_LOCK 内、调用方事务中执行）：重读账单最新状态
     * （可能已被其他路径补过/停用/删除），未到期/不可补返回 0，否则 while 循环补账。
     */
    private int catchUpFresh(Long billId) {
        RecurringBill b = billRepository.findById(billId).orElse(null);
        if (b == null || b.getDeleted() != 0 || b.getEnabled() != 1
                || b.getNextDate() == null || b.getNextDate().compareTo(today()) > 0) {
            return 0;
        }
        return catchUp(b);
    }

    /**
     * 单账单补账循环：while next_date <= today 逐周期生成流水并推进。
     * 仅由 catchUpFresh 在 SCAN_LOCK 内、事务中调用；不做账单状态复核（调用方已重读校验）。
     */
    private int catchUp(RecurringBill b) {
        String today = today();
        int count = 0;
        while (b.getNextDate().compareTo(today) <= 0) {
            if (++count > MAX_CATCHUP_CYCLES) { // 脏数据兜底：直接跳到下一周期出循环
                log.warn("周期账 #{} 补账超过 {} 个周期，中止", b.getId(), MAX_CATCHUP_CYCLES);
                break;
            }
            LedgerEntry e = postEntry(b);
            b.setLastEntryId(e.getId());
            b.setNextDate(advance(b.getNextDate(), b.getCycle()));
        }
        billRepository.save(b);
        return count;
    }

    /** 周期账 → 账本流水（addManual 等价语义：type/category/amountFen/accountId 直落，remark 落账单备注）。 */
    private LedgerEntry postEntry(RecurringBill b) {
        LedgerEntry e = new LedgerEntry();
        e.setKitchenId(b.getKitchenId());
        e.setType(b.getType());
        e.setSource(LedgerEntry.SOURCE_RECURRING);
        e.setCategory(b.getCategory());
        e.setAmountFen(b.getAmountFen());
        e.setDineDate(b.getNextDate());
        e.setRemark(b.getRemark() == null ? "" : b.getRemark());
        e.setCurrency("CNY");
        if (b.getAccountId() != null) { // 账户可能在建单后被删：挂失效账户不如不挂
            e.setAccountId(accountRepository.findByIdAndDeleted(b.getAccountId(), 0)
                    .map(Account::getId).orElse(null));
        }
        e.setCreatedAt(now());
        return ledgerRepository.save(e);
    }

    /** 推进一个周期：MONTHLY 加一月（0131→0228 兜底）；YEARLY 加一年（0229→0228 自动兜底）。 */
    private String advance(String date, String cycle) {
        LocalDate d = LocalDate.parse(date);
        LocalDate next;
        if (RecurringBill.CYCLE_YEARLY.equals(cycle)) {
            next = d.plusYears(1);
        } else {
            int day = d.getDayOfMonth();
            YearMonth ym = YearMonth.from(d).plusMonths(1);
            next = ym.atDay(Math.min(day, ym.lengthOfMonth()));
        }
        return next.toString();
    }

    // ---------- 校验 ----------

    /** 按 req 非空字段填充/校验账单（create 全量、update 部分）。 */
    private void applyReq(RecurringBill b, Long kitchenId, Long userId, BillReq req) {
        if (req == null) {
            throw new BusinessException("请求不能为空");
        }
        if (req.type() != null) {
            if (!LedgerEntry.TYPE_INCOME.equals(req.type())
                    && !LedgerEntry.TYPE_EXPENSE.equals(req.type())) {
                throw new BusinessException("类型只能是收入或支出");
            }
            b.setType(req.type());
        }
        if (req.amountFen() != null) {
            if (req.amountFen() <= 0) {
                throw new BusinessException("金额必须大于 0");
            }
            b.setAmountFen(req.amountFen());
        }
        if (req.cycle() != null) {
            if (!RecurringBill.CYCLE_MONTHLY.equals(req.cycle())
                    && !RecurringBill.CYCLE_YEARLY.equals(req.cycle())) {
                throw new BusinessException("周期只能是 MONTHLY 或 YEARLY");
            }
            b.setCycle(req.cycle());
        }
        if (b.getType() == null || b.getAmountFen() == null || b.getCycle() == null) {
            throw new BusinessException("请完整填写类型、金额与周期");
        }
        if (req.category() != null) {
            if (req.category().isBlank()) {
                throw new BusinessException("请输入分类");
            }
            String cat = req.category().trim();
            if (cat.length() > 20) {
                throw new BusinessException("分类名最多20个字");
            }
            // 分类须属于本厨房该类型（listCategories 顺带懒加载默认分类）
            boolean allowed = ledgerService.listCategories(userId, kitchenId).stream()
                    .anyMatch(c -> c.type().equals(b.getType()) && c.name().equals(cat));
            if (!allowed) {
                throw new BusinessException("不支持的分类，可在分类管理中添加");
            }
            b.setCategory(cat);
        }
        if (b.getCategory() == null || b.getCategory().isBlank()) {
            throw new BusinessException("请输入分类");
        }
        if (req.nextDate() != null) {
            if (!req.nextDate().trim().matches("\\d{4}-\\d{2}-\\d{2}")) {
                throw new BusinessException("日期格式应为 YYYY-MM-DD");
            }
            b.setNextDate(req.nextDate().trim());
        } else if (b.getNextDate() == null) {
            b.setNextDate(today()); // 缺省从今天起
        }
        if (req.accountId() != null) { // 传 null 无法区分「清空」与「不动」，按不动处理；换绑传具体 id
            Account a = accountRepository.findByIdAndDeleted(req.accountId(), 0)
                    .orElseThrow(() -> new BusinessException(404, "账户不存在"));
            if (!a.getKitchenId().equals(kitchenId)) {
                throw new BusinessException(403, "账户不属于本厨房");
            }
            b.setAccountId(req.accountId());
        }
        if (req.remark() != null) {
            b.setRemark(req.remark().trim());
        }
    }

    private RecurringBill owned(Long kitchenId, Long billId) {
        RecurringBill b = billRepository.findById(billId)
                .orElseThrow(() -> new BusinessException(404, "周期账不存在"));
        if (!b.getKitchenId().equals(kitchenId)) {
            throw new BusinessException(403, "无权操作该周期账");
        }
        return b;
    }

    private String today() {
        return LocalDate.now().toString();
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

    private BillView toView(RecurringBill b) {
        return new BillView(b.getId(), b.getType(), b.getCategory(), b.getAmountFen(),
                b.getAccountId(), b.getCycle(), b.getNextDate(), b.getRemark(),
                b.getLastEntryId(), b.getEnabled());
    }

    // ---------- 视图 / 请求 ----------

    public record BillView(Long id, String type, String category, Long amountFen,
                           Long accountId, String cycle, String nextDate, String remark,
                           Long lastEntryId, Integer enabled) {}

    /** enabled 传 0/1；PUT 只更新传入（非 null）字段。 */
    public record BillReq(String type, String category, Long amountFen, Long accountId,
                          String cycle, String nextDate, String remark, Integer enabled) {}
}
