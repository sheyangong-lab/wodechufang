package com.sharedkitchen.module.ledger;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavingRepository extends JpaRepository<Saving, Long> {

    List<Saving> findByKitchenIdAndDeletedOrderByIdDesc(Long kitchenId, Integer deleted);
}
