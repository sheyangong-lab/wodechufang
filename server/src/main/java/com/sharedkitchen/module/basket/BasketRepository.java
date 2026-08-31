package com.sharedkitchen.module.basket;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BasketRepository extends JpaRepository<BasketItem, Long> {

    List<BasketItem> findByKitchenIdOrderByCheckedAscCreatedAtAsc(Long kitchenId);

    Optional<BasketItem> findByKitchenIdAndName(Long kitchenId, String name);

    void deleteByKitchenId(Long kitchenId);
}
