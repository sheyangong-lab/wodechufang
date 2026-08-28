package com.sharedkitchen.module.vip;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RedeemCodeRepository extends JpaRepository<RedeemCode, Long> {

    Optional<RedeemCode> findByCode(String code);

    List<RedeemCode> findByBatchOrderByCreatedAtDesc(String batch);
}
