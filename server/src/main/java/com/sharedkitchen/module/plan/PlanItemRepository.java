package com.sharedkitchen.module.plan;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanItemRepository extends JpaRepository<PlanItem, Long> {

    List<PlanItem> findByKitchenIdAndPlanDateOrderBySlotIndexAscCreatedAtAsc(Long kitchenId, String planDate);

    List<PlanItem> findByKitchenIdOrderByPlanDateDesc(Long kitchenId);
}
