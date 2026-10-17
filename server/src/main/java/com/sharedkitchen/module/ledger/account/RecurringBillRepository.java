package com.sharedkitchen.module.ledger.account;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecurringBillRepository extends JpaRepository<RecurringBill, Long> {

    /** 某厨房全部周期账（含停用），前端列表用；next_date 过滤在服务层做。 */
    List<RecurringBill> findByKitchenIdAndDeletedOrderByNextDateAscIdAsc(Long kitchenId, Integer deleted);

    /** 定时任务全量扫描：启用中的账单（跨厨房）。 */
    List<RecurringBill> findByDeletedAndEnabled(Integer deleted, Integer enabled);
}
