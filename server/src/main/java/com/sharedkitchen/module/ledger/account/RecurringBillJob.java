package com.sharedkitchen.module.ledger.account;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每天早上 8:05（北京时间）周期账扫描 + 预算超支检查。
 * 与冰箱临期任务（ExpiryScanJob 8:00）错峰 5 分钟。
 */
@Component
public class RecurringBillJob {

    private final RecurringBillService recurringBillService;
    private final LedgerBudgetService ledgerBudgetService;

    public RecurringBillJob(RecurringBillService recurringBillService,
                            LedgerBudgetService ledgerBudgetService) {
        this.recurringBillService = recurringBillService;
        this.ledgerBudgetService = ledgerBudgetService;
    }

    @Scheduled(cron = "0 5 8 * * ?", zone = "Asia/Shanghai")
    public void scanDaily() {
        recurringBillService.scanOnce();
        ledgerBudgetService.notifyOverruns();
    }
}
