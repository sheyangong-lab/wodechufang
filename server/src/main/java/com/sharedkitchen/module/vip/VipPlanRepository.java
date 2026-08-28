package com.sharedkitchen.module.vip;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VipPlanRepository extends JpaRepository<VipPlan, Long> {

    List<VipPlan> findByEnabledTrueOrderBySortAsc();
}
