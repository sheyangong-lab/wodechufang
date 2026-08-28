package com.sharedkitchen.module.order;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByKitchenIdOrderByCreatedAtDesc(Long kitchenId);

    List<Order> findByKitchenIdAndDineDateOrderByCreatedAtDesc(Long kitchenId, String dineDate);

    List<Order> findByKitchenIdAndStatusOrderByCreatedAtDesc(Long kitchenId, String status);

    List<Order> findByKitchenIdAndDineDateAndStatusOrderByCreatedAtDesc(
            Long kitchenId, String dineDate, String status);

    List<Order> findByKitchenIdAndBuyerIdOrderByCreatedAtDesc(Long kitchenId, Long buyerId);

    List<Order> findByKitchenIdAndBuyerIdAndDineDateOrderByCreatedAtDesc(
            Long kitchenId, Long buyerId, String dineDate);

    List<Order> findByKitchenIdAndBuyerIdAndStatusOrderByCreatedAtDesc(
            Long kitchenId, Long buyerId, String status);

    List<Order> findByKitchenIdAndBuyerIdAndDineDateAndStatusOrderByCreatedAtDesc(
            Long kitchenId, Long buyerId, String dineDate, String status);
}
