package com.sharedkitchen.module.dish;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DishRepository extends JpaRepository<Dish, Long> {

    List<Dish> findByKitchenIdAndDeletedOrderByUpdatedAtDesc(Long kitchenId, Integer deleted);

    List<Dish> findByKitchenIdAndDeletedAndStatusOrderByUpdatedAtDesc(
            Long kitchenId, Integer deleted, Integer status);

    long countByKitchenIdAndDeleted(Long kitchenId, Integer deleted);

    long countByKitchenIdAndCategoryIdAndDeleted(Long kitchenId, Long categoryId, Integer deleted);
}
