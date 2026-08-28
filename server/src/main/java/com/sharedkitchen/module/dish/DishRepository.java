package com.sharedkitchen.module.dish;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DishRepository extends JpaRepository<Dish, Long> {

    /** 广场池：全平台分享到广场且上架未删除的菜谱（跨厨房公开）。 */
    List<Dish> findByShareSquareAndDeletedAndStatusOrderByUpdatedAtDesc(
            Integer shareSquare, Integer deleted, Integer status);

    List<Dish> findByKitchenIdAndDeletedOrderByUpdatedAtDesc(Long kitchenId, Integer deleted);

    List<Dish> findByKitchenIdAndDeletedAndStatusOrderByUpdatedAtDesc(
            Long kitchenId, Integer deleted, Integer status);

    long countByKitchenIdAndDeleted(Long kitchenId, Integer deleted);

    long countByKitchenIdAndCategoryIdAndDeleted(Long kitchenId, Long categoryId, Integer deleted);
}
