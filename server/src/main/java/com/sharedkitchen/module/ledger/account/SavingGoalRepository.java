package com.sharedkitchen.module.ledger.account;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavingGoalRepository extends JpaRepository<SavingGoal, Long> {

    List<SavingGoal> findByKitchenIdAndDeletedOrderByCreatedAtDescIdDesc(Long kitchenId, Integer deleted);
}
