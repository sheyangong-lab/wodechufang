package com.sharedkitchen.module.ledger.account;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findByKitchenIdAndDeletedOrderBySortAscIdAsc(Long kitchenId, Integer deleted);

    Optional<Account> findByIdAndDeleted(Long id, Integer deleted);

    long countByKitchenId(Long kitchenId);

    boolean existsByKitchenIdAndNameAndDeleted(Long kitchenId, String name, Integer deleted);
}
