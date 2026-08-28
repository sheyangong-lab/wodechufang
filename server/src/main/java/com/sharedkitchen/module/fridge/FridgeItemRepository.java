package com.sharedkitchen.module.fridge;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FridgeItemRepository extends JpaRepository<FridgeItem, Long> {

    @org.springframework.data.jpa.repository.Query(
            "select distinct f.kitchenId from FridgeItem f where f.deleted = 0")
    List<Long> findKitchenIdsWithItems();

    List<FridgeItem> findByKitchenIdAndDeletedOrderByCreatedAtDesc(Long kitchenId, Integer deleted);

    List<FridgeItem> findByKitchenIdAndDeletedAndCategoryIdOrderByCreatedAtDesc(
            Long kitchenId, Integer deleted, Long categoryId);

    List<FridgeItem> findByKitchenIdAndDeletedAndNameContainingOrderByCreatedAtDesc(
            Long kitchenId, Integer deleted, String keyword);

    List<FridgeItem> findByKitchenIdAndDeletedAndCategoryIdAndNameContainingOrderByCreatedAtDesc(
            Long kitchenId, Integer deleted, Long categoryId, String keyword);

    List<FridgeItem> findByKitchenIdAndDeleted(Long kitchenId, Integer deleted);
}
