package com.sharedkitchen.module.kitchen;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KitchenMemberRepository extends JpaRepository<KitchenMember, Long> {

    List<KitchenMember> findByKitchenIdOrderByJoinedAtAsc(Long kitchenId);

    List<KitchenMember> findByUserIdOrderByJoinedAtAsc(Long userId);

    Optional<KitchenMember> findByKitchenIdAndUserId(Long kitchenId, Long userId);

    long countByKitchenId(Long kitchenId);
}
