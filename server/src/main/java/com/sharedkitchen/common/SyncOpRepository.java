package com.sharedkitchen.common;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncOpRepository extends JpaRepository<SyncOp, Long> {
    Optional<SyncOp> findByOpId(String opId);
}
