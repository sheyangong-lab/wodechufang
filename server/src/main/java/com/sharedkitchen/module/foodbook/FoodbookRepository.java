package com.sharedkitchen.module.foodbook;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodbookRepository extends JpaRepository<FoodbookItem, Long> {

    List<FoodbookItem> findByKitchenIdAndPageDateOrderByIdAsc(Long kitchenId, String pageDate);
}
