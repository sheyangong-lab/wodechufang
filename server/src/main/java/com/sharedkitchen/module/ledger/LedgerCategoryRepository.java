package com.sharedkitchen.module.ledger;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerCategoryRepository extends JpaRepository<LedgerCategory, Long> {

    List<LedgerCategory> findByKitchenIdOrderByTypeAscIdAsc(Long kitchenId);

    long countByKitchenId(Long kitchenId);
}
