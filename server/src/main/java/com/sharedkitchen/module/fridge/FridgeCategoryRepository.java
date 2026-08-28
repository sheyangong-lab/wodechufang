package com.sharedkitchen.module.fridge;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FridgeCategoryRepository extends JpaRepository<FridgeCategory, Long> {

    List<FridgeCategory> findByKitchenIdOrderBySortAscIdAsc(Long kitchenId);

    long countByKitchenId(Long kitchenId);
}
