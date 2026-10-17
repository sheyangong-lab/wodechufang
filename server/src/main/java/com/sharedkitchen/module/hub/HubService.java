package com.sharedkitchen.module.hub;

import com.sharedkitchen.module.basket.BasketItem;
import com.sharedkitchen.module.basket.BasketService;
import com.sharedkitchen.module.fridge.FridgeService;
import com.sharedkitchen.module.fridge.FridgeService.FridgeItemView;
import com.sharedkitchen.module.ledger.LedgerService;
import com.sharedkitchen.module.ledger.LedgerService.MonthSummary;
import com.sharedkitchen.module.plan.PlanService;
import com.sharedkitchen.module.plan.PlanService.PlanDay;
import com.sharedkitchen.module.plan.PlanService.PlanItemView;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/** 中枢首页聚合：一次请求拉齐 钱/冰箱/今晚/买菜 四格数据（纯读，无新表）。 */
@Service
public class HubService {

    private final LedgerService ledgerService;
    private final FridgeService fridgeService;
    private final BasketService basketService;
    private final PlanService planService;

    public HubService(LedgerService ledgerService, FridgeService fridgeService,
                      BasketService basketService, PlanService planService) {
        this.ledgerService = ledgerService;
        this.fridgeService = fridgeService;
        this.basketService = basketService;
        this.planService = planService;
    }

    public HubOverviewView overview(Long userId, Long kitchenId) {
        String month = LocalDate.now().toString().substring(0, 7);
        MonthSummary ms = ledgerService.summary(userId, kitchenId, month);

        List<FridgeItemView> items = fridgeService.listItems(userId, kitchenId, null, null, "all");
        List<String> expiringNames = items.stream()
                .filter(i -> "expiring".equals(i.state()))
                .map(FridgeItemView::name)
                .limit(3)
                .toList();
        FridgeOverview fridge = new FridgeOverview(items.size(),
                (int) items.stream().filter(i -> "expiring".equals(i.state())).count(),
                expiringNames);

        List<BasketItem> basket = basketService.list(userId, kitchenId);
        List<String> pendingNames = basket.stream()
                .filter(b -> b.getChecked() == 0)
                .map(BasketItem::getName)
                .limit(3)
                .toList();
        BasketOverview basketOverview = new BasketOverview(
                (int) basket.stream().filter(b -> b.getChecked() == 0).count(), pendingNames);

        return new HubOverviewView(ms, fridge, tonight(userId, kitchenId), basketOverview);
    }

    /** 今晚吃什么：取今天餐计划里槽位名含"晚"的最后一项，否则取最后一项，没排菜返回 null。 */
    private TonightView tonight(Long userId, Long kitchenId) {
        try {
            PlanDay day = planService.day(userId, kitchenId, LocalDate.now().toString());
            if (day == null || day.items().isEmpty()) return null;
            List<String> slots = day.slots();
            PlanItemView pick = null;
            for (PlanItemView it : day.items()) {
                String slotName = it.slotIndex() != null && it.slotIndex() < slots.size()
                        ? slots.get(it.slotIndex()) : "";
                if (slotName != null && slotName.contains("晚")) pick = it;
            }
            if (pick == null) pick = day.items().get(day.items().size() - 1);
            return new TonightView(pick.name(), pick.imageUrl());
        } catch (Exception e) {
            return null; // 餐段未配置等场景不阻塞中枢
        }
    }

    public record HubOverviewView(MonthSummary month, FridgeOverview fridge,
                                  TonightView tonight, BasketOverview basket) {}
    public record FridgeOverview(int total, int expiring, List<String> expiringNames) {}
    public record TonightView(String name, String imageUrl) {}
    public record BasketOverview(int pendingCount, List<String> pendingNames) {}
}
