package com.sharedkitchen.module.ledger.account;

import jakarta.transaction.Transactional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    /** 某厨房某月的全部预算行（category 为 NULL 的是总预算行）。 */
    List<Budget> findByKitchenIdAndMonth(Long kitchenId, String month);

    /** 设了某月预算（总/分类任一）的厨房 id，供定时任务遍历。 */
    @Query("select distinct b.kitchenId from Budget b where b.month = :month")
    List<Long> findKitchenIdsByMonth(@Param("month") String month);

    /**
     * 整体覆盖保存的前置清空：JPQL 批量删除立即执行。
     * 不能用 deleteAll(Iterable)：Hibernate 刷新时 INSERT 先于 DELETE，会同月重插撞 uk_budget 唯一索引。
     */
    @Transactional
    @Modifying
    @Query("delete from Budget b where b.kitchenId = :kid and b.month = :month")
    void deleteByKitchenIdAndMonth(@Param("kid") Long kitchenId, @Param("month") String month);
}
