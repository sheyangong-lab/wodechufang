package com.sharedkitchen.module.kitchen;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KitchenRepository extends JpaRepository<Kitchen, Long> {

    Optional<Kitchen> findByCode(String code);
}
