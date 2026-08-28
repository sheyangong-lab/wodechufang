package com.sharedkitchen.module.fridge;

import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每天早上 8 点（北京时间）扫描所有有食材的厨房，生成临期站内通知（同日去重）。 */
@Component
public class ExpiryScanJob {

    private final FridgeItemRepository itemRepository;
    private final FridgeService fridgeService;

    public ExpiryScanJob(FridgeItemRepository itemRepository, FridgeService fridgeService) {
        this.itemRepository = itemRepository;
        this.fridgeService = fridgeService;
    }

    @Scheduled(cron = "0 0 8 * * ?", zone = "Asia/Shanghai")
    public void scanDaily() {
        List<Long> kitchenIds = itemRepository.findKitchenIdsWithItems();
        kitchenIds.forEach(fridgeService::checkExpiry);
    }
}
