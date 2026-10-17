package com.sharedkitchen.module.ledger;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByKitchenIdAndDineDateStartingWithOrderByDineDateDescCreatedAtDesc(
            Long kitchenId, String month);

    List<LedgerEntry> findByKitchenIdOrderByDineDateDescCreatedAtDesc(Long kitchenId);

    Optional<LedgerEntry> findByOrderIdAndType(Long orderId, String type);

    boolean existsByKitchenIdAndTypeAndDineDateStartingWith(
            Long kitchenId, String type, String month);

    boolean existsByAccountId(Long accountId);

    /**
     * 各账户流水累计（账户余额推导）：
     * INCOME/REFUND 记 +amount；TRANSFER 入账（transfer_dir='in'）记 +amount；
     * 其余（EXPENSE、TRANSFER 出账）记 −amount。
     */
    @Query("select e.accountId, sum(case when e.type in ('INCOME','REFUND') "
            + "or (e.type = 'TRANSFER' and e.transferDir = 'in') "
            + "then e.amountFen else -e.amountFen end) "
            + "from LedgerEntry e where e.kitchenId = :kid and e.accountId is not null "
            + "group by e.accountId")
    List<Object[]> sumFlowByAccountId(@Param("kid") Long kitchenId);
}
