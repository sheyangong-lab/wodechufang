package com.sharedkitchen.module.fridge;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop50ByKitchenIdOrderByCreatedAtDesc(Long kitchenId);

    long countByKitchenIdAndIsRead(Long kitchenId, Integer isRead);

    /** 预算超支提醒按标题判重（每厨房每月只提醒一次）。 */
    boolean existsByKitchenIdAndTitle(Long kitchenId, String title);
}
