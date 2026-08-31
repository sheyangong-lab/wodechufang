package com.sharedkitchen.module.plan;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanConfigRepository extends JpaRepository<PlanConfig, Long> {
    Optional<PlanConfig> findByKitchenId(Long kitchenId);
}
