package com.sharedkitchen.module.ledger;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByKitchenIdAndDineDateStartingWithOrderByDineDateDescCreatedAtDesc(
            Long kitchenId, String month);

    List<LedgerEntry> findByKitchenIdOrderByDineDateDescCreatedAtDesc(Long kitchenId);

    Optional<LedgerEntry> findByOrderIdAndType(Long orderId, String type);

    boolean existsByKitchenIdAndTypeAndDineDateStartingWith(
            Long kitchenId, String type, String month);
}
