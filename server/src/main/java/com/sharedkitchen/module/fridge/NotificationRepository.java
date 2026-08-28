package com.sharedkitchen.module.fridge;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop50ByKitchenIdOrderByCreatedAtDesc(Long kitchenId);

    long countByKitchenIdAndIsRead(Long kitchenId, Integer isRead);
}
