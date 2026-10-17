package com.sharedkitchen.module.ledger.account;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findByKitchenIdAndDeletedOrderBySortAscIdAsc(Long kitchenId, Integer deleted);

    Optional<Account> findByIdAndDeleted(Long id, Integer deleted);

    long countByKitchenId(Long kitchenId);

    boolean existsByKitchenIdAndNameAndDeleted(Long kitchenId, String name, Integer deleted);

    /**
     * 默认账户原子种子：同名账户已存在则插 0 行（SQLite/PG 通用的 INSERT...SELECT...WHERE NOT EXISTS），
     * 防并发首访 ensureDefaults 双插。判重不含 deleted 过滤——与 countByKitchenId 判重语义一致，
     * 账户全删（软删）后不再复活默认账户。
     */
    @Modifying
    @Query(value = "INSERT INTO accounts (kitchen_id, name, type, initial_fen, sort, deleted, created_at) "
            + "SELECT :kitchenId, :name, :type, 0, :sort, 0, :createdAt WHERE NOT EXISTS "
            + "(SELECT 1 FROM accounts WHERE kitchen_id = :kitchenId AND name = :name)",
            nativeQuery = true)
    int insertDefaultIfAbsent(@Param("kitchenId") Long kitchenId, @Param("name") String name,
                              @Param("type") String type, @Param("sort") int sort,
                              @Param("createdAt") String createdAt);
}
