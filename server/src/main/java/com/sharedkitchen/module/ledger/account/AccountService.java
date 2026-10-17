package com.sharedkitchen.module.ledger.account;

import com.sharedkitchen.common.BusinessException;
import com.sharedkitchen.module.kitchen.KitchenMember;
import com.sharedkitchen.module.kitchen.KitchenMemberRepository;
import com.sharedkitchen.module.ledger.LedgerEntryRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 账本账户（三钱包）：余额 = initialFen + 流水累计。
 * 方向语义：INCOME/REFUND 记 +amount；EXPENSE 记 −amount；TRANSFER 由 transfer_dir 决定（in→+，out→−）。
 */
@Service
public class AccountService {

    private static final int MAX_ACCOUNTS = 20;

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerRepository;
    private final KitchenMemberRepository memberRepository;

    public AccountService(AccountRepository accountRepository,
                          LedgerEntryRepository ledgerRepository,
                          KitchenMemberRepository memberRepository) {
        this.accountRepository = accountRepository;
        this.ledgerRepository = ledgerRepository;
        this.memberRepository = memberRepository;
    }

    /** 账户列表（含实时余额）；新厨房首次访问懒加载四个默认账户。 */
    @Transactional
    public List<AccountView> list(Long userId, Long kitchenId) {
        requireMember(kitchenId, userId);
        ensureDefaults(kitchenId);
        Map<Long, Long> flows = flowSums(kitchenId);
        return accountRepository.findByKitchenIdAndDeletedOrderBySortAscIdAsc(kitchenId, 0).stream()
                .map(a -> toView(a, flows.getOrDefault(a.getId(), 0L)))
                .toList();
    }

    @Transactional
    public AccountView create(Long userId, Long kitchenId, String name, String type,
                              Long initialFen, Long creditLimitFen) {
        requireMember(kitchenId, userId);
        ensureDefaults(kitchenId);
        if (!Account.TYPE_DISPOSABLE.equals(type)
                && !Account.TYPE_SAVING.equals(type)
                && !Account.TYPE_CREDIT.equals(type)) {
            throw new BusinessException("账户类型只能是 DISPOSABLE/SAVING/CREDIT");
        }
        String trimmed = validName(name);
        List<Account> existing = accountRepository.findByKitchenIdAndDeletedOrderBySortAscIdAsc(kitchenId, 0);
        if (existing.stream().anyMatch(a -> a.getName().equals(trimmed))) {
            throw new BusinessException("该账户名已存在");
        }
        if (existing.size() >= MAX_ACCOUNTS) {
            throw new BusinessException("账户最多 " + MAX_ACCOUNTS + " 个");
        }
        Long limit = null;
        if (Account.TYPE_CREDIT.equals(type)) {
            if (creditLimitFen != null && creditLimitFen < 0) {
                throw new BusinessException("信用额度不能为负数");
            }
            limit = creditLimitFen;
        }
        Account a = new Account();
        a.setKitchenId(kitchenId);
        a.setName(trimmed);
        a.setType(type);
        a.setInitialFen(initialFen == null ? 0L : initialFen);
        a.setCreditLimitFen(limit);
        a.setSort(existing.stream().map(Account::getSort).filter(s -> s != null)
                .max(Integer::compareTo).orElse(0) + 1);
        a.setCreatedAt(now());
        accountRepository.save(a);
        return toView(a, 0L);
    }

    /** 名称/排序/期初/额度；仅更新传入（非 null）字段。 */
    @Transactional
    public AccountView update(Long userId, Long kitchenId, Long aid, String name, Integer sort,
                              Long initialFen, Long creditLimitFen) {
        requireMember(kitchenId, userId);
        Account a = owned(kitchenId, aid);
        if (name != null && !name.isBlank()) {
            String trimmed = validName(name);
            if (!trimmed.equals(a.getName())
                    && accountRepository.existsByKitchenIdAndNameAndDeleted(kitchenId, trimmed, 0)) {
                throw new BusinessException("该账户名已存在");
            }
            a.setName(trimmed);
        }
        if (sort != null) {
            a.setSort(sort);
        }
        if (initialFen != null) {
            a.setInitialFen(initialFen);
        }
        if (creditLimitFen != null) {
            if (creditLimitFen < 0) {
                throw new BusinessException("信用额度不能为负数");
            }
            if (Account.TYPE_CREDIT.equals(a.getType())) {
                a.setCreditLimitFen(creditLimitFen);
            }
        }
        accountRepository.save(a);
        Map<Long, Long> flows = flowSums(kitchenId);
        return toView(a, flows.getOrDefault(a.getId(), 0L));
    }

    /** 软删；账户名下已有流水则拒绝（历史流水依赖账户存在）。 */
    @Transactional
    public void delete(Long userId, Long kitchenId, Long aid) {
        requireMember(kitchenId, userId);
        Account a = owned(kitchenId, aid);
        if (ledgerRepository.existsByAccountId(aid)) {
            throw new BusinessException("该账户已有流水，无法删除");
        }
        a.setDeleted(1);
        accountRepository.save(a);
    }

    /** 资产总览：负债为正数；净资产 = disposable + saving − creditDebt。 */
    @Transactional
    public AssetsView assets(Long userId, Long kitchenId) {
        requireMember(kitchenId, userId);
        ensureDefaults(kitchenId);
        Map<Long, Long> flows = flowSums(kitchenId);
        long disposable = 0, saving = 0, creditBalance = 0, creditLimit = 0;
        for (Account a : accountRepository.findByKitchenIdAndDeletedOrderBySortAscIdAsc(kitchenId, 0)) {
            long balance = (a.getInitialFen() == null ? 0 : a.getInitialFen())
                    + flows.getOrDefault(a.getId(), 0L);
            if (Account.TYPE_DISPOSABLE.equals(a.getType())) {
                disposable += balance;
            } else if (Account.TYPE_SAVING.equals(a.getType())) {
                saving += balance;
            } else if (Account.TYPE_CREDIT.equals(a.getType())) {
                creditBalance += balance;
                creditLimit += a.getCreditLimitFen() == null ? 0 : a.getCreditLimitFen();
            }
        }
        long debt = -creditBalance; // CREDIT 组余额为负表示欠款，取反得正数负债
        return new AssetsView(disposable, saving, debt, creditLimit,
                creditLimit - debt, disposable + saving - debt);
    }

    // ---------- 内部 ----------

    /** 新厨房首次访问时种子默认账户（V19 迁移只覆盖迁移时的存量厨房）。
     *  并发防护见 seed()：原子插入，两个并发首访也不会双插。 */
    private void ensureDefaults(Long kitchenId) {
        if (accountRepository.countByKitchenId(kitchenId) > 0) {
            return;
        }
        String t = now();
        seed(kitchenId, "现金", Account.TYPE_DISPOSABLE, 1, t);
        seed(kitchenId, "微信", Account.TYPE_DISPOSABLE, 2, t);
        seed(kitchenId, "支付宝", Account.TYPE_DISPOSABLE, 3, t);
        seed(kitchenId, "存款", Account.TYPE_SAVING, 10, t);
    }

    /** INSERT...WHERE NOT EXISTS 原子种子：同名（含软删）已存在时插 0 行。 */
    private void seed(Long kitchenId, String name, String type, int sort, String t) {
        accountRepository.insertDefaultIfAbsent(kitchenId, name, type, sort, t);
    }

    /** 各账户流水累计（方向语义见类注释），一条聚合 JPQL 取回。 */
    private Map<Long, Long> flowSums(Long kitchenId) {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : ledgerRepository.sumFlowByAccountId(kitchenId)) {
            long sum = row[1] == null ? 0L : ((Number) row[1]).longValue();
            map.put((Long) row[0], sum);
        }
        return map;
    }

    private AccountView toView(Account a, long flowFen) {
        long initial = a.getInitialFen() == null ? 0 : a.getInitialFen();
        return new AccountView(a.getId(), a.getName(), a.getType(),
                a.getInitialFen(), a.getCreditLimitFen(), a.getSort(), initial + flowFen);
    }

    private Account owned(Long kitchenId, Long aid) {
        Account a = accountRepository.findByIdAndDeleted(aid, 0)
                .orElseThrow(() -> new BusinessException(404, "账户不存在"));
        if (!a.getKitchenId().equals(kitchenId)) {
            throw new BusinessException(403, "无权操作该账户");
        }
        return a;
    }

    private String validName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException("请输入账户名称");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 10) {
            throw new BusinessException("账户名最多10个字");
        }
        return trimmed;
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

    public record AccountView(Long id, String name, String type, Long initialFen,
                              Long creditLimitFen, Integer sort, Long balanceFen) {}

    public record AssetsView(Long disposableFen, Long savingFen, Long creditDebtFen,
                             Long creditLimitFen, Long creditRemainFen, Long netAssetFen) {}
}
