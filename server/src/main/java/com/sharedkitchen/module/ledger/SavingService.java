package com.sharedkitchen.module.ledger;

import com.sharedkitchen.common.BusinessException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 存钱罐：各币种余额汇总 + 存入/取出流水；余额折算人民币展示由视图端按汇率计算。 */
@Service
public class SavingService {

    private final SavingRepository repo;
    private final com.sharedkitchen.module.kitchen.KitchenMemberRepository memberRepository;

    public SavingService(SavingRepository repo,
                         com.sharedkitchen.module.kitchen.KitchenMemberRepository memberRepository) {
        this.repo = repo;
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

    public List<SavingView> list(Long userId, Long kitchenId) {
        requireMember(kitchenId, userId);
        return repo.findByKitchenIdAndDeletedOrderByIdDesc(kitchenId, 0).stream()
                .map(s -> new SavingView(s.getId(), s.getName(), s.getCurrency(),
                        s.getAmountFen(), s.getKind(), s.getNote(), s.getCreatedAt()))
                .toList();
    }

    /** 汇总：按币种合计（存入-取出）+ 折合人民币总额。 */
    public Map<String, Object> summary(Long userId, Long kitchenId) {
        requireMember(kitchenId, userId);
        long cny = 0, hkd = 0, usd = 0;
        List<SavingView> recent = new ArrayList<>();
        for (Saving s : repo.findByKitchenIdAndDeletedOrderByIdDesc(kitchenId, 0)) {
            long signed = s.getKind().equals(Saving.KIND_WITHDRAW) ? -s.getAmountFen() : s.getAmountFen();
            switch (s.getCurrency()) {
                case "HKD" -> hkd += signed;
                case "USD" -> usd += signed;
                default -> cny += signed;
            }
            if (recent.size() < 20) {
                recent.add(new SavingView(s.getId(), s.getName(), s.getCurrency(),
                        s.getAmountFen(), s.getKind(), s.getNote(), s.getCreatedAt()));
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("balanceCnyFen", cny);
        out.put("balanceHkdFen", hkd);
        out.put("balanceUsdFen", usd);
        out.put("recent", recent);
        return out;
    }

    @Transactional
    public SavingView add(Long userId, Long kitchenId, String name, String currency,
                          Long amountFen, String kind, String note) {
        requireMember(kitchenId, userId);
        if (amountFen == null || amountFen <= 0) {
            throw new BusinessException("金额必须大于 0");
        }
        String cur = currency == null || currency.isBlank() ? "CNY" : currency.toUpperCase();
        if (!cur.equals("CNY") && !cur.equals("HKD") && !cur.equals("USD")) {
            throw new BusinessException("币种只支持 CNY/HKD/USD");
        }
        String k = kind == null || kind.isBlank() ? Saving.KIND_DEPOSIT : kind;
        if (!k.equals(Saving.KIND_DEPOSIT) && !k.equals(Saving.KIND_WITHDRAW)) {
            throw new BusinessException("类型只能是 DEPOSIT 或 WITHDRAW");
        }
        Saving s = new Saving();
        s.setKitchenId(kitchenId);
        s.setName(name == null || name.isBlank() ? "存钱" : name.trim());
        s.setCurrency(cur);
        s.setAmountFen(amountFen);
        s.setKind(k);
        s.setNote(note == null ? "" : note.trim());
        s.setCreatedBy(userId);
        s.setCreatedAt(Instant.now().toString());
        repo.save(s);
        return new SavingView(s.getId(), s.getName(), s.getCurrency(), s.getAmountFen(),
                s.getKind(), s.getNote(), s.getCreatedAt());
    }

    @Transactional
    public void remove(Long userId, Long savingId) {
        Saving s = repo.findById(savingId).orElseThrow(() -> new BusinessException(404, "记录不存在"));
        requireMember(s.getKitchenId(), userId);
        s.setDeleted(1);
        repo.save(s);
    }
}
