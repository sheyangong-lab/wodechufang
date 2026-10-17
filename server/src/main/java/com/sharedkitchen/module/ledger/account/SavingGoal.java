package com.sharedkitchen.module.ledger.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 存款目标：progress = 全部 SAVING 账户余额之和（多目标共用同一进度）。 */
@Entity
@Table(name = "saving_goals")
public class SavingGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kitchen_id", nullable = false)
    private Long kitchenId;

    @Column(nullable = false, length = 20)
    private String name;

    @Column(name = "target_fen", nullable = false)
    private Long targetFen;

    /** 截止日期 YYYY-MM-DD；可空。 */
    @Column(length = 10)
    private String deadline;

    @Column(nullable = false)
    private Integer deleted = 0;

    @Column(name = "created_at", nullable = false)
    private String createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getKitchenId() { return kitchenId; }
    public void setKitchenId(Long kitchenId) { this.kitchenId = kitchenId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getTargetFen() { return targetFen; }
    public void setTargetFen(Long targetFen) { this.targetFen = targetFen; }
    public String getDeadline() { return deadline; }
    public void setDeadline(String deadline) { this.deadline = deadline; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
