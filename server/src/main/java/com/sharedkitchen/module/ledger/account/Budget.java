package com.sharedkitchen.module.ledger.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 月度预算（分）：category 为 NULL 表示总预算行，非 NULL 表示分类预算行；每厨房每月整体覆盖保存。 */
@Entity
@Table(name = "budgets")
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kitchen_id", nullable = false)
    private Long kitchenId;

    /** 月份 YYYY-MM。 */
    @Column(nullable = false, length = 7)
    private String month;

    /** 分类名；NULL = 总预算（SQLite 唯一索引按 IFNULL(category,'~总~') 判重）。 */
    @Column(length = 20)
    private String category;

    @Column(name = "amount_fen", nullable = false)
    private Long amountFen;

    @Column(name = "created_at", nullable = false)
    private String createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getKitchenId() { return kitchenId; }
    public void setKitchenId(Long kitchenId) { this.kitchenId = kitchenId; }
    public String getMonth() { return month; }
    public void setMonth(String month) { this.month = month; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Long getAmountFen() { return amountFen; }
    public void setAmountFen(Long amountFen) { this.amountFen = amountFen; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
