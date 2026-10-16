package com.sharedkitchen.module.ledger;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {

    List<PaymentMethod> findByKitchenIdOrderBySortAscIdAsc(Long kitchenId);

    Optional<PaymentMethod> findByKitchenIdAndName(Long kitchenId, String name);
}
