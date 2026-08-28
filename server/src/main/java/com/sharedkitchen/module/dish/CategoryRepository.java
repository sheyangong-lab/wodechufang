package com.sharedkitchen.module.dish;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByKitchenIdOrderBySortAscIdAsc(Long kitchenId);
}
